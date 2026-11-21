package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import javax.imageio.ImageIO;
import androidx.compose.ui.graphics.Paint;
public class ReflectionTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var r=new RootRasterTest.Raster(630,488);float[] alpha={1};
  Paint paint=(Paint)Proxy.newProxyInstance(Paint.class.getClassLoader(),new Class[]{Paint.class},(p,m,a)->switch(m.getName()){
   case "setAlpha"->{alpha[0]=(Float)a[0];yield null;}case "getAlpha"->alpha[0];default->throw new AssertionError(m);
  });
  var reflection=new ReflectionRenderer(()->paint);
  RootRenderer.paint(r.scope,1,r::source,(s,x,y,d)->false,r::path);
  reflection.draw(r.scope,420,630,64,4,()->RootRenderer.paint(r.scope,1,r::source,(s,x,y,d)->false,r::path));
  check((r.bitmap.getRGB(500,450)>>>24)>0,"CD reflected to right of cover");
  int near=r.bitmap.getRGB(100,427)>>>24,far=r.bitmap.getRGB(100,480)>>>24;
  check(near>far&&near<55&&far<10,"reflection fades gently to transparent");
  check(r.bitmap.getRGB(100,422)==0,"gap below cover stays clear");
  check(r.bitmap.getRGB(620,450)==0,"outside disc silhouette is transparent");
  check(r.saved.isEmpty()&&r.graphics.getClip()==null&&r.graphics.getTransform().isIdentity(),"all layers and transforms restored");
  Path out=Path.of(args[0]);Files.createDirectories(out.getParent());ImageIO.write(r.bitmap,"png",out.toFile());
  // A reused renderer must follow cover size, reflection height and gap changes.
  for(float[] geometry:new float[][]{{420,64,4},{420,64,4},{360,64,4},{360,90,4},{360,90,12},{420,64,4}}){
   var reused=new RootRasterTest.Raster(630,488);
   var fresh=new RootRasterTest.Raster(630,488);
   reflection.draw(reused.scope,geometry[0],630,geometry[1],geometry[2],
    ()->RootRenderer.paint(reused.scope,1,reused::source,(s,x,y,d)->false,reused::path));
   new ReflectionRenderer(()->paint).draw(fresh.scope,geometry[0],630,geometry[1],geometry[2],
    ()->RootRenderer.paint(fresh.scope,1,fresh::source,(s,x,y,d)->false,fresh::path));
   check(java.util.Arrays.equals(reused.bitmap.getRGB(0,0,630,488,null,0,630),
     fresh.bitmap.getRGB(0,0,630,488,null,0,630)),"reused gradient matches fresh rendering after geometry changes");
  }
  verifyClippedDiscSkip(paint);
  verifyReturnReflection(reflection);
  verifySharedCoverCorners(reflection);
  var preview=new RootRasterTest.Raster(RenderWarmup.WIDTH,RenderWarmup.HEIGHT);
  var appearance=new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE);
  RenderWarmup.draw(preview.scope,preview::path,preview::source,new ReflectionRenderer(()->paint),appearance);
  check(preview.sourceCalls==4,"warmup draws cover and generated artwork in the main and reflected passes");
  check(preview.bitmap.getRGB(0,0)==0,"warmup exercises the live rounded-cover clip");
  check((preview.bitmap.getRGB(108,106)>>>24)>0,"warmup exercises visible disc reflection pixels");
  check(preview.saved.isEmpty(),"warmup restores the private raster canvas");
  System.out.println("PASS offscreen warmup draw sequence: cover, generated artwork and reflection");
  System.out.println("PASS production ReflectionRenderer + RootRenderer: mirrored cover and CD silhouette, 18% layer, vertical fade, transparent gap/outside, fully restored Canvas");
  System.out.println("Actual host gradient Brush used with Java2D Canvas/Paint adapter; Windows compositing awaits real-machine validation.");
 }
 private static void verifyReturnReflection(ReflectionRenderer renderer)throws Exception{
  int side=420, width=630, shortFade=64, gap=4;
  var start=ReflectionRenderer.returning(side,side,shortFade,gap,0);
  var moving=ReflectionRenderer.returning(side,side,shortFade,gap,.5f);
  var end=ReflectionRenderer.returning(side,side,shortFade,gap,1);
  var before=returnRaster(renderer,1f,start,640);
  var during=returnRaster(renderer,1f,moving,640);
  var finalFrame=returnRaster(renderer,1f,end,640);
  check(before.bitmap.getRGB(40,510)==0,"original short mirror ends above the comparison pixel");
  int mid=during.bitmap.getRGB(40,510)>>>24,last=finalFrame.bitmap.getRGB(40,510)>>>24;
  check(mid>0&&last>mid,"the longer cover reflection is already visible midway through shrink");
  check(Math.abs((finalFrame.bitmap.getRGB(40,420)>>>24)-38)<=1,"the small-cover reflection touches the edge at native opacity");
  check(finalFrame.bitmap.getRGB(40,630)==0,"native reflection fades over half the displayed cover height");
  check((finalFrame.bitmap.getRGB(40,434)&255)>200,"bottom strip of the original is mirrored by the edge");
  check(finalFrame.bitmap.getRGB(450,500)==0,"the returned reflection has only the cover width");

  var clipped=returnRaster(renderer,1f,end,544);
  check(clipped.bitmap.getRGB(40,510)==finalFrame.bitmap.getRGB(40,510)
       &&clipped.bitmap.getRGB(40,544)==0,"viewport clips the reflection without compressing its gradient");
  var landscape=ReflectionRenderer.returning(side,210,shortFade,gap,1);
  var wide=returnRaster(renderer,2f,landscape,640);
  check(landscape.top()==315&&landscape.bottom()==420,"landscape mirror uses the artwork edge, not its square layout");
  check((wide.bitmap.getRGB(40,315)>>>24)>0&&wide.bitmap.getRGB(40,420)==0,
      "landscape reflection stays directly below its cover");

  var again=returnRaster(renderer,1f,start,640);
  check(java.util.Arrays.equals(before.bitmap.getRGB(0,0,width,640,null,0,width),
      again.bitmap.getRGB(0,0,width,640,null,0,width)),"reopening restores the original short gradient and opacity");
  System.out.println("PASS continuous return reflection: native-length fade available during shrink, glued to cover edge, viewport clip and regular profile restored");
 }
 private static RootRasterTest.Raster returnRaster(ReflectionRenderer renderer,float aspect,
     ReflectionRenderer.Band band,float clipBottom)throws Exception{
  var r=new RootRasterTest.Raster(630,640);
  renderer.draw(r.scope,420,band,clipBottom,()->RootRenderer.paint(r.scope,aspect,r::source,(s,x,y,d)->false,r::path,0));
  check(r.saved.isEmpty()&&r.graphics.getClip()==null&&r.graphics.getTransform().isIdentity(),"return reflection restores drawing state");
  return r;
 }
 private static void verifySharedCoverCorners(ReflectionRenderer renderer)throws Exception{
  int side=420;
  for(float scale:new float[]{1f,.8f,.6f})for(float aspect:new float[]{.75f,1f,1.5f}){
   var r=new RootRasterTest.Raster(630,650);
   var corners=new RootRenderer.CoverCorners();
   float density=1.25f, radius=CoverHandoff.coverCornerRadius(scale,density);
   check(Math.abs(radius*scale-15f)<.001f,"expanded, moving and native-size covers share the same on-screen rounding");
   r.graphics.scale(scale,scale);
   var pose=r.graphics.getTransform();
   ReflectionRenderer.Content draw=()->RootRenderer.paint(r.scope,aspect,r::source,(s,x,y,d)->false,r::path,
       0,50,false,null,0,DiscSettings.Style.CD,DiscSettings.VinylColor.BLACK,false,DiscSettings.ReelColor.DEFAULT,corners,radius);
   draw.draw();
   float width=RootRenderer.fittedWidth(side,aspect),height=RootRenderer.fittedHeight(side,aspect);
   var band=ReflectionRenderer.returning(side,height,64,4,1);
   renderer.draw(r.scope,side,band,650,draw);
   check(r.masks.size()==1,"the main cover and its mirror reuse exactly one clip");
   int left=Math.round((side-width)*.5f*scale),right=Math.round((side+width)*.5f*scale);
   int top=Math.round((side-height)*.5f*scale),bottom=Math.round((side+height)*.5f*scale);
   for(int x:new int[]{left+1,right-2})for(int y:new int[]{top+1,bottom-2})
    check(r.bitmap.getRGB(x,y)==0,"all four corners are rounded at each scale and aspect");
   check((r.bitmap.getRGB(left+18,top+1)>>>24)==255,"round clipping preserves artwork along the top edge");
   check(r.bitmap.getRGB(left+1,bottom+1)==0&&(r.bitmap.getRGB(left+18,bottom+1)>>>24)>0,
       "the reflection mirrors the rounded bottom corner without a second silhouette");
   check(r.saved.isEmpty()&&r.graphics.getClip()==null&&r.graphics.getTransform().equals(pose),
       "cover rounding and reflection leave the outer transform and clip intact");
  }
  System.out.println("PASS uniform rounded cover across zoom levels, three aspect ratios, native radius, and a shared reflection silhouette");
 }
 private static void verifyClippedDiscSkip(Paint paint)throws Exception{
  int side=240, amount=50, width=360, height=28, gap=4, canvasHeight=side+gap+height;
  int skipped=0, retained=0;
  var live=FirstRevealPreparationTest.liveFinishes();
  var vinylCache=RootRenderer.Finishes.class.getDeclaredField("vinyl");
  var reelCache=RootRenderer.Finishes.class.getDeclaredField("reel");
  vinylCache.setAccessible(true);
  reelCache.setAccessible(true);
  var reflection=new ReflectionRenderer(()->paint);
  for(var style:DiscSettings.Style.values()){
   var color=style==DiscSettings.Style.COLORED_VINYL?DiscSettings.VinylColor.BLUE:DiscSettings.VinylColor.BLACK;
   for(float exposed:new float[]{0.05f,0.15f,0.25f,0.35f,0.45f,0.55f,0.75f,1f}){
    // The test adapter stores path geometry on its owner raster.
    vinylCache.set(live,null);
    reelCache.set(live,null);
    var reference=new RootRasterTest.Raster(width,canvasHeight);
    var optimized=new RootRasterTest.Raster(width,canvasHeight);
    var reelColor=DiscSettings.ReelColor.DEFAULT;
    int[] calls={0};
    reflection.draw(reference.scope,side,width,height,gap,()->{
     RootRenderer.paint(reference.scope,1f,reference::source,(s,x,y,d)->false,reference::path,
         exposed,amount,false,null,0f,style,color,false,reelColor);
    });
    optimized.masks.putAll(reference.masks);
    optimized.outlines.putAll(reference.outlines);
    float visible=RootRenderer.reflectedExposure(optimized.scope,exposed,amount,side-height);
    reflection.draw(optimized.scope,side,width,height,gap,()->{
     RootRenderer.paint(optimized.scope,1f,optimized::source,(s,x,y,d)->{calls[0]++;return false;},optimized::path,
         visible,amount,false,null,0f,style,color,false,reelColor);
    });
    check(java.util.Arrays.equals(reference.bitmap.getRGB(0,0,width,canvasHeight,null,0,width),
        optimized.bitmap.getRGB(0,0,width,canvasHeight,null,0,width)),
        "reflection pixels match when skipping clipped artwork for "+style+" at "+exposed);
    if(visible==0){
     check(calls[0]==0,"fully clipped reflection skips the disc draw");
     skipped++;
    }else{
     check(calls[0]==1,"potentially visible reflection draws the disc");
     retained++;
    }
   }
  }
  check(skipped>0&&retained>0,"exercise both reflection branches");
  System.out.println("PASS clipped disc reflection: "+skipped+" skips and "+retained+" retained draws, matching pixels");
 }
}
