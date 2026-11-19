package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.SkiaBackedCanvas_skikoKt;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.graphics.SkiaImageAsset_skikoKt;
import androidx.compose.ui.graphics.Path;
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.util.function.Supplier;
import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.Surface;

/** Exercises the cold drawing path on a small private raster before page entry. */
final class RenderWarmup {
 static final int SIDE=96, WIDTH=SIDE*2, HEIGHT=SIDE+18;
 private RenderWarmup(){}

 static void run(DiscSettings.Values settings)throws Exception{
  try(var surface=Surface.Companion.makeRasterN32Premul(WIDTH,HEIGHT);var bitmap=new Bitmap()){
   if(!bitmap.allocN32Pixels(8,8,false))throw new IllegalStateException("Cannot allocate warmup bitmap");
   bitmap.erase(0xff334455);
   bitmap.setImmutable();
   var image=SkiaImageAsset_skikoKt.asComposeImageBitmap(bitmap);
   var scope=new CanvasDrawScope();
   var params=scope.getDrawParams();
   params.setCanvas(SkiaBackedCanvas_skikoKt.asComposeCanvas(surface.getCanvas()));
   params.getClass().getMethod("setSize-uvyYCjk",long.class).invoke(params,RootRenderer.pack(WIDTH,HEIGHT));
   draw(scope,SkiaBackedPath_skikoKt::Path,
       (target,w,h)->DiscImage.drawBitmap(target,image,8,8,w*.5f,h*.5f,Math.max(w,h)),
       new ReflectionRenderer(),settings.appearance());
  }
 }

 static void draw(DrawScope scope,Supplier<Path> paths,RootRenderer.Source cover,ReflectionRenderer reflection,
     DiscSettings.Appearance appearance)throws Exception{
  var style=appearance.style();
  var vinyl=appearance.finish()==DiscSettings.VinylColor.RANDOM?DiscSettings.VinylColor.BLUE:appearance.finish();
  var reel=appearance.reelColor()==DiscSettings.ReelColor.RANDOM?DiscSettings.ReelColor.DEFAULT:appearance.reelColor();
  RootRenderer.Disc generated=(target,x,y,d)->false;
  ReflectionRenderer.Content paint=()->RootRenderer.paint(scope,1f,cover,generated,paths,.6f,100,false,null,0f,
      style,vinyl,false,reel);
  paint.draw();
  reflection.draw(scope,SIDE,WIDTH,14,4,paint);
 }
}
