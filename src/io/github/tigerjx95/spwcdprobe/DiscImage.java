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
 private DiscImage(Bitmap bitmap){nativeBitmap=bitmap;this.bitmap=SkiaImageAsset_skikoKt.asComposeImageBitmap(bitmap);width=bitmap.getWidth();height=bitmap.getHeight();}
 static DiscImage decode(byte[] bytes,int width,int height)throws Exception{
  Bitmap bitmap=null;
  try(Image image=Image.Companion.makeFromEncoded(bytes)){
   if(image.getWidth()!=width||image.getHeight()!=height)throw new IllegalStateException("PNG dimensions changed");
   bitmap=Bitmap.Companion.makeFromImage(image);DiscImage result=new DiscImage(bitmap);bitmap=null;return result;
  }finally{if(bitmap!=null)bitmap.close();}
 }
 static DiscImage capture(Shader shader,float sourceWidth,float sourceHeight)throws Exception{
  if(shader==null||shader.isClosed())throw new IllegalStateException("Host shader is closed");
  float factor=Math.min(1,2048f/Math.max(sourceWidth,sourceHeight));
  int w=Math.max(1,Math.round(sourceWidth*factor)),h=Math.max(1,Math.round(sourceHeight*factor));
  Bitmap bitmap=null;
  try(Surface surface=Surface.Companion.makeRasterN32Premul(w,h);Paint paint=new Paint()){
   paint.setShader(shader);
   var canvas=surface.getCanvas();canvas.scale(w/sourceWidth,h/sourceHeight);
   canvas.drawRect(new Rect(0,0,sourceWidth,sourceHeight),paint);
   try(Image image=surface.makeImageSnapshot()){
    bitmap=Bitmap.Companion.makeFromImage(image);DiscImage result=new DiscImage(bitmap);bitmap=null;return result;
   }
  }finally{if(bitmap!=null)bitmap.close();}
 }
 float aspect(){return (float)width/height;}
 void drawCover(DrawScope scope,float w,float h)throws Exception{
  DRAW_IMAGE.invoke(scope,bitmap,0L,pack(width,height),0L,pack(Math.max(1,Math.round(w)),Math.max(1,Math.round(h))),1f,Fill.INSTANCE,null,3);
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
