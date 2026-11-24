package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.SkiaImageAsset_skikoKt;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import androidx.compose.ui.graphics.drawscope.DrawStyle;
import androidx.compose.ui.graphics.drawscope.Fill;
import androidx.compose.ui.graphics.ColorFilter;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.Shader;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.ColorAlphaType;
import java.lang.reflect.Method;
/** Owns decoded local pixels. The service lock prevents close while drawing. */
final class DiscImage implements AutoCloseable {
 private static final Method DRAW_IMAGE;
 static{
  try{DRAW_IMAGE=DrawScope.class.getMethod("drawImage-9jGpkUE",ImageBitmap.class,long.class,long.class,long.class,long.class,float.class,DrawStyle.class,ColorFilter.class,int.class);}
  catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 private final Bitmap nativeBitmap;
 private final ImageBitmap bitmap;
 private final int width,height;
 private CoverWhiteBorders.Bounds coverBounds;
 private CoverWhiteBorders.Mode coverMode;
 private DiscImage(Bitmap bitmap){nativeBitmap=bitmap;this.bitmap=SkiaImageAsset_skikoKt.asComposeImageBitmap(bitmap);width=bitmap.getWidth();height=bitmap.getHeight();}
 static ImageBitmap loadResource(String path){
  try(var stream=DiscImage.class.getResourceAsStream(path)){
   if(stream==null)return null;
   try(Image image=Image.Companion.makeFromEncoded(stream.readAllBytes())){
    return SkiaImageAsset_skikoKt.toComposeImageBitmap(image);
   }
  }catch(Exception|LinkageError unavailable){
   return null;
  }
 }
 static DiscImage decode(byte[] bytes,int width,int height)throws Exception{
  var prepared=DiscArtwork.prepare(bytes,width,height);
  try{bytes=DiscArtwork.pixels(prepared);width=height=prepared.getWidth();}finally{prepared.flush();}
  Bitmap bitmap=null;
  try{
   bitmap=new Bitmap();
   if(!bitmap.installPixels(new ImageInfo(width,height,ColorType.BGRA_8888,ColorAlphaType.PREMUL),bytes,width*4))
    throw new IllegalStateException("Cannot install disc pixels");
   bitmap.setImmutable();
   DiscImage result=new DiscImage(bitmap);bitmap=null;return result;
  }finally{if(bitmap!=null)bitmap.close();}
 }
 static DiscImage capture(Shader shader,float sourceWidth,float sourceHeight)throws Exception{
  if(shader==null||shader.isClosed())throw new IllegalStateException("Host shader is closed");
  if(!Float.isFinite(sourceWidth)||!Float.isFinite(sourceHeight)||sourceWidth<=0||sourceHeight<=0)
   throw new IllegalArgumentException("Invalid host image dimensions");
  float factor=Math.min(1,(float)DiscArtwork.MAX_SIDE/Math.max(sourceWidth,sourceHeight));
  int w=Math.max(1,Math.round(sourceWidth*factor)),h=Math.max(1,Math.round(sourceHeight*factor));
  Bitmap bitmap=null;
  try(Surface surface=Surface.Companion.makeRasterN32Premul(w,h);Paint paint=new Paint()){
   paint.setShader(shader);
   var canvas=surface.getCanvas();canvas.scale(w/sourceWidth,h/sourceHeight);
   canvas.drawRect(new Rect(0,0,sourceWidth,sourceHeight),paint);
   try(Image image=surface.makeImageSnapshot()){
    bitmap=Bitmap.Companion.makeFromImage(image);bitmap.setImmutable();DiscImage result=new DiscImage(bitmap);bitmap=null;return result;
   }
  }finally{if(bitmap!=null)bitmap.close();}
 }
 Image snapshot(){return Image.Companion.makeFromBitmap(nativeBitmap);}
 float aspect(){return (float)width/height;}
 CoverWhiteBorders.Bounds labelBounds(VinylLabelFraming.Crop crop){return crop.pixels(width,height);}
 java.awt.image.BufferedImage labelPreview(){return labelPreview(nativeBitmap);}
 static java.awt.image.BufferedImage labelPreview(Bitmap bitmap){
  try(var original=Image.Companion.makeFromBitmap(bitmap)){return labelPreview(original,480);}
 }
 static java.awt.image.BufferedImage labelPreview(Image original,int maxSize){
  int sourceWidth=original.getWidth(),sourceHeight=original.getHeight();
  float scale=Math.min(1,(float)maxSize/Math.max(sourceWidth,sourceHeight));
  int w=Math.max(1,Math.round(sourceWidth*scale)),h=Math.max(1,Math.round(sourceHeight*scale));
  try(var surface=Surface.Companion.makeRasterN32Premul(w,h)){
   surface.getCanvas().clear(0);
   surface.getCanvas().drawImageRect(original,0,0,sourceWidth,sourceHeight,0,0,w,h,
       org.jetbrains.skia.SamplingMode.Companion.getLINEAR(),null,true);
   try(var thumbnail=surface.makeImageSnapshot();var pixels=Bitmap.Companion.makeFromImage(thumbnail)){
    byte[] rgba=pixels.readPixels(new ImageInfo(w,h,ColorType.RGBA_8888,ColorAlphaType.UNPREMUL),w*4,0,0);
    if(rgba==null)return null;
    var preview=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
    int[] argb=((java.awt.image.DataBufferInt)preview.getRaster().getDataBuffer()).getData();
    for(int i=0,j=0;i<argb.length;i++,j+=4)
     argb[i]=(rgba[j+3]&255)<<24|(rgba[j]&255)<<16|(rgba[j+1]&255)<<8|(rgba[j+2]&255);
    return preview;
   }
  }
 }
 void drawCover(DrawScope scope,float w,float h)throws Exception{
  DRAW_IMAGE.invoke(scope,bitmap,0L,pack(width,height),0L,pack(Math.max(1,Math.round(w)),Math.max(1,Math.round(h))),1f,Fill.INSTANCE,null,3);
 }
 CoverWhiteBorders.Bounds coverBounds(CoverWhiteBorders.Mode mode){
  if(mode!=coverMode){
   var found=CoverWhiteBorders.find(width,height,nativeBitmap::getColor,mode);
   coverBounds=found.trimmedFrom(width,height)?found:null;
   coverMode=mode;
  }
  return coverBounds;
 }
 void drawTrimmedCover(DrawScope scope,float w,float h,CoverWhiteBorders.Bounds bounds)throws Exception{
  DRAW_IMAGE.invoke(scope,bitmap,pack(bounds.x(),bounds.y()),pack(bounds.width(),bounds.height()),0L,
   pack(Math.max(1,Math.round(w)),Math.max(1,Math.round(h))),1f,Fill.INSTANCE,null,3);
 }
 void draw(DrawScope scope,float centerX,float centerY,float diameter)throws Exception{drawBitmap(scope,bitmap,width,height,centerX,centerY,diameter);}
 static void drawBitmap(DrawScope scope,ImageBitmap bitmap,int width,int height,float cx,float cy,float diameter)throws Exception{
  float scale=diameter/Math.max(width,height);int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
  int x=Math.round(cx-w*.5f),y=Math.round(cy-h*.5f);
  DRAW_IMAGE.invoke(scope,bitmap,0L,pack(width,height),pack(x,y),pack(w,h),1f,Fill.INSTANCE,null,3);
 }
 private static long pack(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
 @Override public void close(){nativeBitmap.close();}
}
