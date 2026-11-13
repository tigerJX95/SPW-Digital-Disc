package io.github.tigerjx95.spwcdprobe;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public class VinylRendererTest {
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  var raster=new RootRasterTest.Raster(800,400);
  RootRenderer.paint(raster.scope,2,(s,w,h)->{
   raster.sourceCalls++;
   raster.graphics.setColor(Color.RED);
   raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
  },(s,x,y,d)->false,raster::path,1,100,false,null,0,true);
  check(raster.sourceCalls==2,"draws the album sleeve and one proportional label");
  check(raster.bitmap.getRGB(604,225)==Color.RED.getRGB(),"cover fills the round label");
  int flank=raster.bitmap.getRGB(735,200)&255;
  int lowerLight=raster.bitmap.getRGB(600,335)&255;
  int upperLight=raster.bitmap.getRGB(600,65)&255;
  check(flank<25&&lowerLight>flank+25&&upperLight>flank+25,"dark flanks and two broad directional reflections");
  var rotated=new RootRasterTest.Raster(840,420);
  RootRenderer.paint(rotated.scope,2,rotated::source,(s,x,y,d)->false,rotated::path,1,100,false,null,90,true);
  check((rotated.bitmap.getRGB(630,350)&255)>(rotated.bitmap.getRGB(770,210)&255)+25,"light stays fixed when record rotates");
  check(raster.bitmap.getRGB(600,200)==0,"spindle hole is transparent");
  for(int y=193;y<=207;y++)for(int x=593;x<=607;x++){
   double distance=Math.hypot(x+.5-600,y+.5-200);
   if(distance>=6.7&&distance<=7.3)
    check(raster.bitmap.getRGB(x,y)==0xffdedede,"opaque neutral rim meets cutout without red label fringe");
  }
  check(raster.saved.isEmpty(),"restores Canvas");
  ImageIO.write(raster.bitmap,"png",Path.of(args[0]).toFile());

  var ringPixels=new java.util.HashSet<Integer>();
  var ringOpacities=new java.util.HashSet<Integer>();
  var palettePixels=new java.util.HashSet<Integer>();
  for(var color:DiscSettings.VinylColor.values()){
   var sample=new RootRasterTest.Raster(800,400);
   RootRenderer.paint(sample.scope,2,sample::source,(s,x,y,d)->false,sample::path,1,100,false,null,90,true,color);
   check(palettePixels.add(sample.bitmap.getRGB(600,65)),"each color refreshes the cached finish");
   if(color!=DiscSettings.VinylColor.BLACK){
    int ring=sample.bitmap.getRGB(680,200);
    check(ringPixels.add(ring),"runout has a distinct tint for "+color);
    ringOpacities.add(ring>>>24);
   }
   if(color==DiscSettings.VinylColor.PINK){
    int ring=sample.bitmap.getRGB(680,200);
    int face=sample.bitmap.getRGB(735,200);
    int red=ring>>>16&255,green=ring>>>8&255,blue=ring&255;
    check(red>green+28&&blue>green&&red>(face>>>16&255),"runout is a brighter, more saturated pink");
    check((ring>>>24)>(face>>>24)&&(ring>>>24)<180,"pink runout remains translucent");
   }
   check(sample.sourceCalls==2&&sample.bitmap.getRGB(600,200)==0,"colored vinyl keeps cover label and transparent hole");
  }
  check(ringOpacities.size()==1,"colored runouts share one opacity");
  var blue=new RootRasterTest.Raster(800,400);
  RootRenderer.paint(blue.scope,2,blue::source,(s,x,y,d)->false,blue::path,1,100,false,null,0,true,DiscSettings.VinylColor.BLUE);
  int body=blue.bitmap.getRGB(735,200);
  int runout=blue.bitmap.getRGB(680,200);
  check((body>>>24)<115&&(body>>>24)>85&&(body&255)>150,"cyan body stays colored and translucent in shadow");
  check((runout&255)>150&&(runout>>>24)>(body>>>24)&&(runout>>>24)<180,"smooth tinted runout remains visible around the label");
  ImageIO.write(blue.bitmap,"png",Path.of(args[0]).resolveSibling("ColoredVinyl.png").toFile());
  int highlight=blue.bitmap.getRGB(500,88);
  blue.graphics.setComposite(java.awt.AlphaComposite.Clear);
  blue.graphics.fillRect(0,0,800,400);
  blue.graphics.setComposite(java.awt.AlphaComposite.SrcOver);
  RootRenderer.paint(blue.scope,2,blue::source,(s,x,y,d)->false,blue::path,1,100,false,null,90,true,DiscSettings.VinylColor.BLUE);
  check(highlight==blue.bitmap.getRGB(500,88)&&body==blue.bitmap.getRGB(735,200),"colored face keeps fixed lighting with no rotating blotches");
  blue.graphics.setComposite(java.awt.AlphaComposite.Src);
  blue.graphics.setColor(Color.WHITE);
  blue.graphics.fillRect(0,0,800,400);
  blue.graphics.setComposite(java.awt.AlphaComposite.SrcOver);
  RootRenderer.paint(blue.scope,2,blue::source,(s,x,y,d)->false,blue::path,1,100,false,null,0,true,DiscSettings.VinylColor.BLUE);
  int overWhite=blue.bitmap.getRGB(735,200);
  check((overWhite>>>24)==255&&(overWhite>>16&255)-(body>>16&255)*(body>>>24)/255>=135,"light background remains visible through the colored material");
  var custom=new RootRasterTest.Raster(800,400);
  RootRenderer.paint(custom.scope,1,custom::source,(s,x,y,d)->{
   custom.graphics.setColor(Color.MAGENTA);
   custom.graphics.fill(new Rectangle2D.Float(x-10,y-10,20,20));return true;
  },custom::path,1,100,false,null,0,true,DiscSettings.VinylColor.BLUE);
  check(custom.sourceCalls==1&&custom.bitmap.getRGB(600,200)==Color.MAGENTA.getRGB(),"custom artwork takes priority");
  System.out.println("PASS vinyl label crop, dark outer grooves, spindle transparency, restored Canvas and custom priority");
 }
}
