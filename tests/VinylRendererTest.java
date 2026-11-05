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

  var custom=new RootRasterTest.Raster(800,400);
  RootRenderer.paint(custom.scope,1,custom::source,(s,x,y,d)->{
   custom.graphics.setColor(Color.MAGENTA);
   custom.graphics.fill(new Rectangle2D.Float(x-10,y-10,20,20));return true;
  },custom::path,1,100,false,null,0,true);
  check(custom.sourceCalls==1&&custom.bitmap.getRGB(600,200)==Color.MAGENTA.getRGB(),"custom artwork takes priority");
  System.out.println("PASS vinyl label crop, dark outer grooves, spindle transparency, restored Canvas and custom priority");
 }
}
