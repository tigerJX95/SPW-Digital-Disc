package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStreamImpl;

/** Worker-only normalization. Premultiplied alpha avoids dark transparent fringes. */
final class DiscArtwork {
 static final int MAX_SIDE=2048;
 private DiscArtwork(){}
 static BufferedImage prepare(byte[] bytes,int width,int height)throws IOException{
  BufferedImage source=readForDisplay(bytes,width,height);
  int decodedWidth=source.getWidth(),decodedHeight=source.getHeight();
  // Preserve transparent cutouts in full. Opaque scans use a centered square crop,
  // so a rectangular photo fills a circular disc without stretching it.
  boolean transparent=false;
  if(source.getColorModel().hasAlpha()){
   int[] row=new int[decodedWidth];
   outer:for(int y=0;y<decodedHeight;y++){
    source.getRGB(0,y,decodedWidth,1,row,0,decodedWidth);
    for(int pixel:row)if((pixel>>>24)!=255){transparent=true;break outer;}
   }
  }
  int extent=transparent?Math.max(width,height):Math.min(width,height);
  int side=Math.min(MAX_SIDE,extent);
  BufferedImage result=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB_PRE);
  Graphics2D g=result.createGraphics();
  try{
   g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
   g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
   g.setComposite(AlphaComposite.Src);
   if(transparent){
    int w=Math.max(1,(int)Math.round((double)width*side/extent)),h=Math.max(1,(int)Math.round((double)height*side/extent));
    g.drawImage(source,(side-w)/2,(side-h)/2,w,h,null);
   }else{
    int crop=Math.min(decodedWidth,decodedHeight);
    int x=(decodedWidth-crop)/2,y=(decodedHeight-crop)/2;
    g.drawImage(source,0,0,side,side,x,y,x+crop,y+crop,null);
   }
   // DstIn multiplies, rather than replaces, the original PNG alpha. No backing
   // color or artificial center hub is placed under custom artwork.
   BufferedImage mask=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB_PRE);
   Graphics2D m=mask.createGraphics();
   try{
    try{m.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);m.setColor(Color.WHITE);m.fill(new Ellipse2D.Float(0,0,side,side));}finally{m.dispose();}
    g.setComposite(AlphaComposite.DstIn);g.drawImage(mask,0,0,null);
   }finally{mask.flush();}
  }finally{g.dispose();source.flush();}
  return result;
 }
 // Budget the reader's actual destination raster before it is allocated.
 private static BufferedImage readForDisplay(byte[] bytes,int width,int height)throws IOException{
  try(var stream=new ArtworkInput(bytes)){
   var readers=ImageIO.getImageReaders(stream);
   if(!readers.hasNext())throw new IOException("unsupported PNG/JPEG");
   var reader=readers.next();
   try{
    reader.setInput(stream,true,true);
    if(reader.getWidth(0)!=width||reader.getHeight(0)!=height)throw new IOException("image dimensions changed");
    var types=reader.getImageTypes(0);
    if(!types.hasNext())throw new IOException("no image destination type");
    var type=types.next();
    var sample=type.getSampleModel();
    int bytesPerPixel=sample.getNumDataElements()*DataBuffer.getDataTypeSize(sample.getDataType())/8;
    long budget=Math.min(64L*1024*1024,Runtime.getRuntime().maxMemory()/4);
    int step=1;
    while((long)Math.ceilDiv(width,step)*Math.ceilDiv(height,step)*bytesPerPixel>budget)step++;
    var params=reader.getDefaultReadParam();
    params.setDestinationType(type);
    params.setSourceSubsampling(step,step,0,0);
    return reader.read(0,params);
   }finally{reader.dispose();}
  }
 }
 // ImageIO can seek through the bounded byte array without retaining a second encoded copy.
 static final class ArtworkInput extends ImageInputStreamImpl {
  private final byte[] bytes;
  ArtworkInput(byte[] bytes){this.bytes=bytes;}
  @Override public int read()throws IOException{
   checkClosed();
   bitOffset=0;
   return streamPos<bytes.length?Byte.toUnsignedInt(bytes[(int)streamPos++]):-1;
  }
  @Override public int read(byte[] target,int offset,int length)throws IOException{
   checkClosed();
   java.util.Objects.checkFromIndexSize(offset,length,target.length);
   bitOffset=0;
   if(length==0)return 0;
   if(streamPos>=bytes.length)return -1;
   int count=Math.min(length,bytes.length-(int)streamPos);
   System.arraycopy(bytes,(int)streamPos,target,offset,count);
   streamPos+=count;
   return count;
  }
  @Override public long length(){return bytes.length;}
 }
 // Explicit BGRA byte order, independent of CPU endianness. prepare() owns a
 // contiguous INT_ARGB_PRE raster; copying raw channels preserves premultiplication
 // and avoids an intermediate PNG encode/decode (and its alpha round trip).
 static byte[] pixels(BufferedImage image){
  if(image.getType()!=BufferedImage.TYPE_INT_ARGB_PRE||image.getRaster().getParent()!=null)
   throw new IllegalArgumentException("Expected owned premultiplied image");
  int width=image.getWidth(),height=image.getHeight();
  int[] source=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
  if(source.length!=width*height)throw new IllegalArgumentException("Expected packed raster");
  byte[] result=new byte[Math.multiplyExact(source.length,4)];
  for(int i=0,j=0;i<source.length;i++){
   int p=source[i];result[j++]=(byte)p;result[j++]=(byte)(p>>>8);result[j++]=(byte)(p>>>16);result[j++]=(byte)(p>>>24);
  }
  return result;
 }
 static byte[] encode(BufferedImage image)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable");
  return out.toByteArray();
 }
}
