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
  System.out.println("PASS production ReflectionRenderer + RootRenderer: mirrored cover and CD silhouette, 18% layer, vertical fade, transparent gap/outside, fully restored Canvas");
  System.out.println("Actual host gradient Brush used with Java2D Canvas/Paint adapter; Windows compositing awaits real-machine validation.");
 }
}
