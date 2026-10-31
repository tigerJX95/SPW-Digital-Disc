package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.ImageBitmap;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
public class LocalDiscRasterTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  BufferedImage png=ImageIO.read(new ByteArrayInputStream(LocalDiscServiceTest.png(0xffc93659)));
  ImageBitmap image=(ImageBitmap)Proxy.newProxyInstance(ImageBitmap.class.getClassLoader(),new Class[]{ImageBitmap.class},(p,m,a)->switch(m.getName()){
   case "getWidth"->png.getWidth();case "getHeight"->png.getHeight();default->throw new AssertionError(m);
  });
  RootRasterTest.Raster r=new RootRasterTest.Raster(630,420);r.images.put(image,png);
  RootRenderer.paint(r.scope,1,r::source,(scope,x,y,diameter)->{
   check(Math.abs(diameter-420*.98f)<.01f,"disc diameter is 98% of cover side");
   check(Math.abs(x-420)<.01f&&Math.abs(y-210)<.01f,"disc center aligned to full cover right edge");
   check(x+diameter/2<630&&y-diameter/2>0&&y+diameter/2<420,"larger disc still fits Canvas");
   DiscImage.drawBitmap(scope,image,64,64,x,y,diameter);return true;
  });
  check(r.circles==0,"default CD is not drawn under a transparent local disc");
  check(r.sourceCalls==1,"full cover remains in front");
  check(r.bitmap.getRGB(428,210)==0,"local CD central hole exposes background");
  check((r.bitmap.getRGB(580,210)>>>24)!=0,"local CD artwork visible to cover right");
  check((r.bitmap.getRGB(620,30)>>>24)==0,"transparent PNG outer corner remains transparent");
  Path out=Path.of(args[0]);Files.createDirectories(out.getParent());ImageIO.write(r.bitmap,"png",out.toFile());
  System.out.println("PASS production RootRenderer + DiscImage drawing ABI through Java2D adapter: local artwork, transparent center/background, no default CD backing, full cover in front");
  System.out.println("ImageBitmap pixels supplied by a test adapter; real Windows Skia decode/display remains untested.");
 }
}
