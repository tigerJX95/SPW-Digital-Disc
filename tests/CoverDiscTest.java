package io.github.tigerjx95.spwcdprobe;
import java.awt.Color;
import java.nio.file.*;
import javax.imageio.ImageIO;
public class CoverDiscTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  int cases=0;
  for(float aspect:new float[]{.5f,1,2})for(int side:new int[]{180,420,620}){
   var r=new RootRasterTest.Raster(side*3/2,side);
   RootRenderer.paint(r.scope,aspect,r::source,(s,x,y,d)->false,r::path);
   check(r.sourceCalls==2&&r.circles==11,"same source used twice with hub layers only");
   check(r.bitmap.getRGB(side+Math.max(1,side/50),side/2)==0,"hole exposes background on visible half");
   int hub=r.bitmap.getRGB(Math.round(side+side*.49f*.17f),side/2);
   check((hub>>>24)==255&&(hub>>>16&255)>200&&(hub>>>8&255)>200&&(hub&255)>200,"white source stays visible under translucent hub");
   check((r.bitmap.getRGB(Math.round(side*1.45f),side/2)>>>24)!=0,"art visible at outer right");
   check(r.bitmap.getRGB(Math.round(side*1.45f),side/30)==0,"square corners clipped outside disc");
   check(r.saved.isEmpty()&&r.graphics.getClip()==null,"clip/transform fully restored");
   if(aspect==1){
    check(r.bitmap.getRGB(5,5)==Color.RED.getRGB(),"full cover top left");
    check(r.bitmap.getRGB(side-5,side-5)==Color.YELLOW.getRGB(),"full cover bottom right");
    if(side==420){Path out=Path.of(args[0]);Files.createDirectories(out.getParent());ImageIO.write(r.bitmap,"png",out.toFile());}
   }
   cases++;
  }
  // Cropping a wide cover fills the entire disc instead of leaving letterbox gaps.
  var wide=new RootRasterTest.Raster(630,420);float[] sizes=new float[4];int[] n={0};
  RootRenderer.paint(wide.scope,2,(s,w,h)->{sizes[n[0]++]=w;sizes[n[0]++]=h;wide.source(s,w,h);},(s,x,y,d)->false,wide::path);
  check(Math.abs(sizes[0]/sizes[1]-2)<.001&&sizes[1]>400,"disc keeps aspect and fills diameter");
  check(sizes[2]==420&&sizes[3]==210,"front cover remains fully fitted");
  System.out.println("PASS "+cases+" generated-cover disc rasters: circular crop, transparent hole/corners, translucent hub over artwork, full cover preserved, restored Canvas, portrait/square/landscape aspect");
  System.out.println("Uses production renderer with Path/Canvas adapter; Windows native clipping awaits real-machine validation.");
 }
}
