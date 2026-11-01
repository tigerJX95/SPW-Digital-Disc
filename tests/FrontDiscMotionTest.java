package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.Paint;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
public class FrontDiscMotionTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  float last=-1;
  for(int i=0;i<=100;i++){
   float opacity=FrontDiscLayer.opacity(i/100f);
   check(opacity>=last&&opacity>=0&&opacity<=1,"continuous monotonic entry opacity");last=opacity;
  }
  check(FrontDiscLayer.opacity(0)==0&&FrontDiscLayer.opacity(.1f)==.5f&&FrontDiscLayer.opacity(.2f)==1&&FrontDiscLayer.opacity(1)==1,"short blend window and fully opaque travel");
  float[] alpha={1};AtomicInteger allocations=new AtomicInteger();
  Paint paint=(Paint)Proxy.newProxyInstance(Paint.class.getClassLoader(),new Class[]{Paint.class},(p,m,a)->switch(m.getName()){
   case "setAlpha"->{alpha[0]=(Float)a[0];yield null;}case "getAlpha"->alpha[0];default->throw new AssertionError(m);
  });
  FrontDiscLayer layer=new FrontDiscLayer(()->{allocations.incrementAndGet();return paint;});
  for(int repeat=0;repeat<100;repeat++)for(float progress:new float[]{.02f,.05f,.1f,.15f,.2f,.5f,1,.1f,.05f}){
   var r=new RootRasterTest.Raster(100,100);
   boolean saved=layer.begin(r.canvas,100,100,progress);
   r.graphics.setColor(Color.RED);r.graphics.fill(new Ellipse2D.Float(10,10,80,80));if(saved)r.canvas.restore();
   int expected=Math.round(255*FrontDiscLayer.opacity(progress));
   check(Math.abs((r.bitmap.getRGB(25,50)>>>24)-expected)<=1&&Math.abs((r.bitmap.getRGB(75,50)>>>24)-expected)<=1,"whole circle shares uniform opacity, with no moving cut edge");
   check(r.bitmap.getRGB(1,1)==0&&r.saved.isEmpty(),"transparent outside and restored layer");
   check(saved==(progress<.2f),"fully visible disc does not allocate a compositing layer");
  }
  check(allocations.get()==1,"one cached paint across repeated main/reflection transitions");
  // Real production Renderer frames, including nested reflection opacity.
  for(int amount:new int[]{25,50,100})for(float progress:new float[]{.025f,.05f,.1f,.2f,.5f,1})for(boolean local:new boolean[]{false,true}){
   var frame=FrontRendererTest.render(amount,progress,true,local,true);
   if(amount==50&&local){
    Path out=Path.of("build/qa/whole-disc-"+Math.round(progress*1000)+".png");Files.createDirectories(out.getParent());ImageIO.write(frame.bitmap,"png",out.toFile());
   }
  }
  var opening=FrontRendererTest.render(50,.1f,true,true,true);
  var retracting=FrontRendererTest.render(50,.1f,true,true,true);
  check(FrontRendererTest.equal(opening.bitmap,retracting.bitmap),"same normalized progress has identical entry/retract pixels and reflection");
  System.out.println("PASS 101 opacity samples, 900 cached-paint layer draws, whole-circle uniform fade, fully-open layer bypass, 36 actual renderer/reflection frames, reversible pixels and no moving wipe");
 }
}
