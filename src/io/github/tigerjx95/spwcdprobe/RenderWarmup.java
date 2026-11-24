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
 record Target(int width,int height,int amount) {
  static final Target SAMPLE=new Target(WIDTH,HEIGHT,100);

  static Target fromLayout(SelfDrawLayout layout,float density,int amount){
   if(amount==0||!layout.usable(density))return SAMPLE;
   return new Target(Math.round(layout.width()/density*density),
       Math.round(layout.height()/density*density),amount);
  }
 }
 private RenderWarmup(){}

 static void run(DiscSettings.Values settings)throws Exception{
  run(settings.appearance(),Target.SAMPLE);
 }

 static RootRenderer.Finishes run(DiscSettings.Appearance appearance,Target target)throws Exception{
  try(var surface=Surface.Companion.makeRasterN32Premul(WIDTH,HEIGHT);var bitmap=new Bitmap()){
   if(!bitmap.allocN32Pixels(8,8,false))throw new IllegalStateException("Cannot allocate warmup bitmap");
   bitmap.erase(0xff334455);
   bitmap.setImmutable();
   var image=SkiaImageAsset_skikoKt.asComposeImageBitmap(bitmap);
   var scope=new CanvasDrawScope();
   var params=scope.getDrawParams();
   params.setCanvas(SkiaBackedCanvas_skikoKt.asComposeCanvas(surface.getCanvas()));
   params.getClass().getMethod("setSize-uvyYCjk",long.class).invoke(params,RootRenderer.pack(target.width(),target.height()));
   // Use the visible Canvas dimensions for the shader cache without allocating a full-size raster.
   float scale=Math.min((float)WIDTH/target.width(),(float)HEIGHT/target.height());
   params.getCanvas().scale(scale,scale);
   return draw(scope,SkiaBackedPath_skikoKt::Path,
       (destination,w,h)->DiscImage.drawBitmap(destination,image,8,8,w*.5f,h*.5f,Math.max(w,h)),
       new ReflectionRenderer(),appearance,target);
  }
 }

 static void draw(DrawScope scope,Supplier<Path> paths,RootRenderer.Source cover,ReflectionRenderer reflection,
     DiscSettings.Appearance appearance)throws Exception{
  draw(scope,paths,cover,reflection,appearance,Target.SAMPLE);
 }

 static RootRenderer.Finishes draw(DrawScope scope,Supplier<Path> paths,RootRenderer.Source cover,ReflectionRenderer reflection,
     DiscSettings.Appearance appearance,Target target)throws Exception{
  var style=appearance.style();
  var vinyl=appearance.finish()==DiscSettings.VinylColor.RANDOM?DiscSettings.VinylColor.BLUE:appearance.finish();
  var reel=appearance.reelColor()==DiscSettings.ReelColor.RANDOM?DiscSettings.ReelColor.DEFAULT:appearance.reelColor();
  var finishes=new RootRenderer.Finishes();
  RootRenderer.Disc generated=(destination,x,y,d)->false;
  var corners=new RootRenderer.CoverLayer();
  ReflectionRenderer.Content paint=()->RootRenderer.paint(scope,1f,cover,generated,paths,.6f,target.amount(),false,null,0f,
      style,vinyl,false,reel,corners,CoverHandoff.coverCornerRadius(1f,1f),finishes);
  paint.draw();
  float side=target.width()/DiscExposure.widthFactor(target.amount());
  reflection.draw(scope,side,target.width(),Math.min(14,Math.max(0,target.height()-side-4)),4,paint);
  return finishes;
 }
}
