package io.github.tigerjx95.spwcdprobe;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import androidx.compose.ui.graphics.ImageBitmap;
public class DiscExposureTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  check(DiscExposure.percent(null)==50&&DiscExposure.percent("bad")==50&&DiscExposure.percent(Double.NaN)==50&&DiscExposure.percent(Float.POSITIVE_INFINITY)==50,"invalid settings default");
  check(DiscExposure.percent(-1)==0&&DiscExposure.percent(900)==100&&DiscExposure.percent(25.6)==26,"clamp and round");
  int cases=0;
  for(float[] window:new float[][]{{940,600},{1673,930},{1920,1080},{2560,1440}})for(float density:new float[]{1,1.25f,1.5f})for(int amount:new int[]{0,10,25,50,75,90,100}){
   var p=SelfDrawLayout.fit(window[0],window[1],density,true,amount);
   check(p.left()>=0&&p.left()+p.width()<=window[0],"group fits window at every amount");
   check(p.top()>=0&&p.top()+p.height()<=window[1]-90*density,"controls remain outside group");
   check(Math.abs(p.left()+p.width()/2-window[0]/2)<.01f,"group centered");
   if(amount==50)check(p.equals(SelfDrawLayout.fit(window[0],window[1],density,true)),"default layout identical");
   cases++;
  }
  BufferedImage png=ImageIO.read(new ByteArrayInputStream(LocalDiscServiceTest.png(0xffc93659)));
  ImageBitmap image=(ImageBitmap)Proxy.newProxyInstance(ImageBitmap.class.getClassLoader(),new Class[]{ImageBitmap.class},(p,m,a)->switch(m.getName()){
   case "getWidth"->64;case "getHeight"->64;default->throw new AssertionError(m);
  });
  for(int amount:new int[]{0,25,50,75,100})for(boolean local:new boolean[]{false,true}){
   int side=240,w=Math.round(side*DiscExposure.widthFactor(amount));var r=new RootRasterTest.Raster(w,side);r.images.put(image,png);
   AtomicInteger calls=new AtomicInteger();
   RootRenderer.Disc disc=(scope,cx,cy,d)->{
    calls.incrementAndGet();check(Math.abs(cx-DiscExposure.center(side,1,amount))<.01f,"correct physical travel");
    check(cx+d/2<w&&cy-d/2>0&&cy+d/2<side,"full disc fits measured Canvas");
    if(local){DiscImage.drawBitmap(scope,image,64,64,cx,cy,d);return true;}return false;
   };
   RootRenderer.paint(r.scope,1,r::source,disc,r::path,1,amount);
   check(calls.get()==(amount==0?0:1),"zero amount skips all disc drawing");
   check(r.sourceCalls==(local||amount==0?1:2),"complete cover remains independent");
   check(r.bitmap.getRGB(5,5)==Color.RED.getRGB()&&r.bitmap.getRGB(side-5,side-5)==Color.YELLOW.getRGB(),"full cover corners preserved");
   check(r.saved.isEmpty(),"Canvas restored");
   if(amount==100&&local)check(r.bitmap.getRGB(360,120)==0,"full local disc transparent hole preserved");
   if(amount>0)check((r.bitmap.getRGB(w-16,120)>>>24)!=0,"outer disc edge visible at target amount");
   Path output=Path.of("build/qa/exposure-"+amount+(local?"-local":"-generated")+".png");Files.createDirectories(output.getParent());ImageIO.write(r.bitmap,"png",output.toFile());
  }
  // Zero still has the existing cover entry, but must return without a fake retract wait.
  DiscTransition motion=new DiscTransition();long t=1_000_000_000L;motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  AtomicInteger returned=new AtomicInteger();motion.close(t+950_000_000L,returned::incrementAndGet,false);
  check(returned.get()==1&&motion.finished()&&motion.pose().exposed()==0,"cover-only return completes immediately once");
  motion.close(t+960_000_000L,returned::incrementAndGet,false);check(returned.get()==1,"no duplicate native return");
  System.out.println("PASS "+cases+" exposure/window/density layouts, 10 generated/local rasters, 0/25/50/75/100 travel, full cover and transparent hole, zero-amount immediate return, clamps and invalid defaults");
 }
}
