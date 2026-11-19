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
  var preview=new RootRasterTest.Raster(RenderWarmup.WIDTH,RenderWarmup.HEIGHT);
  var appearance=new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE);
  RenderWarmup.draw(preview.scope,preview::path,preview::source,new ReflectionRenderer(()->paint),appearance);
  check(preview.sourceCalls==4,"warmup draws cover and generated artwork in the main and reflected passes");
  check((preview.bitmap.getRGB(108,106)>>>24)>0,"warmup exercises visible disc reflection pixels");
  check(preview.saved.isEmpty(),"warmup restores the private raster canvas");
  System.out.println("PASS offscreen warmup draw sequence: cover, generated artwork and reflection");
  System.out.println("PASS production ReflectionRenderer + RootRenderer: mirrored cover and CD silhouette, 18% layer, vertical fade, transparent gap/outside, fully restored Canvas");
  System.out.println("Actual host gradient Brush used with Java2D Canvas/Paint adapter; Windows compositing awaits real-machine validation.");
 }
 private static void verifyClippedDiscSkip(Paint paint)throws Exception{
  int side=240, amount=50, width=360, height=28, gap=4, canvasHeight=side+gap+height;
  int skipped=0, retained=0;
  var vinylCache=RootRenderer.class.getDeclaredField("cachedVinyl");
  var reelCache=RootRenderer.class.getDeclaredField("cachedReel");
  vinylCache.setAccessible(true);
  reelCache.setAccessible(true);
  var reflection=new ReflectionRenderer(()->paint);
  for(var style:DiscSettings.Style.values()){
   var color=style==DiscSettings.Style.COLORED_VINYL?DiscSettings.VinylColor.BLUE:DiscSettings.VinylColor.BLACK;
   for(float exposed:new float[]{0.05f,0.15f,0.25f,0.35f,0.45f,0.55f,0.75f,1f}){
    // The test adapter stores path geometry on its owner raster.
    vinylCache.set(null,null);
    reelCache.set(null,null);
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
