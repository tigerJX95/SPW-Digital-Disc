package io.github.tigerjx95.spwcdprobe;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Worker-only normalization. Premultiplied alpha avoids dark transparent fringes. */
final class DiscArtwork {
 static final int MAX_SIDE=2048;
 private DiscArtwork(){}
 static BufferedImage prepare(byte[] bytes,int width,int height)throws IOException{
  BufferedImage source=ImageIO.read(new ByteArrayInputStream(bytes));
  if(source==null||source.getWidth()!=width||source.getHeight()!=height)throw new IOException("image dimensions changed");
  // Preserve transparent cutouts in full. Opaque scans use a centered square crop,
  // so a rectangular photo fills a circular disc without stretching it.
  boolean transparent=false;
  if(source.getColorModel().hasAlpha())outer:for(int y=0;y<height;y++)for(int x=0;x<width;x++){
   if((source.getRGB(x,y)>>>24)!=255){transparent=true;break outer;}
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
    int x=(width-extent)/2,y=(height-extent)/2;
    g.drawImage(source,0,0,side,side,x,y,x+extent,y+extent,null);
   }
   // DstIn multiplies, rather than replaces, the original PNG alpha. No backing
   // color or artificial center hub is placed under custom artwork.
   BufferedImage mask=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB_PRE);
   Graphics2D m=mask.createGraphics();
   try{m.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);m.setColor(Color.WHITE);m.fill(new Ellipse2D.Float(0,0,side,side));}finally{m.dispose();}
   g.setComposite(AlphaComposite.DstIn);g.drawImage(mask,0,0,null);
  }finally{g.dispose();source.flush();}
  return result;
 }
 static byte[] encode(BufferedImage image)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable");
  return out.toByteArray();
 }
}
