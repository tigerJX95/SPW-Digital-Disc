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
   if(color==DiscSettings.VinylColor.RANDOM)continue;
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
  comparePreparedSurface();
  System.out.println("PASS vinyl label crop, dark outer grooves, spindle transparency, restored Canvas and custom priority");
 }
 private static void comparePreparedSurface()throws Exception{
  int side=400,radius=196;
  for(var color:new DiscSettings.VinylColor[]{DiscSettings.VinylColor.BLACK,DiscSettings.VinylColor.PINK}){
   var surface=new RootRasterTest.Raster(side*2,side);
   // Match the live pixel origin for the Java2D sweep wedge rasterizer.
   surface.graphics.translate(600-radius,200-radius);
   RootRenderer.bakeVinylSurface(surface.scope,radius,surface::path,color);
   for(float angle:new float[]{0,90}){
    var live=new RootRasterTest.Raster(side*2,side);
    var cached=new RootRasterTest.Raster(side*2,side);
    var style=color==DiscSettings.VinylColor.BLACK?DiscSettings.Style.BLACK_VINYL:DiscSettings.Style.COLORED_VINYL;
    RootRenderer.Disc prepared=new RootRenderer.Disc(){
     public boolean draw(androidx.compose.ui.graphics.drawscope.DrawScope scope,float x,float y,float diameter){return false;}
     public boolean drawVinylSurface(androidx.compose.ui.graphics.drawscope.DrawScope scope,float r){
      check(r==radius,"prepared surface keeps the measured vinyl radius");
      cached.graphics.drawImage(surface.bitmap,null,-600,-200);
      return true;
     }
    };
    RootRenderer.paint(live.scope,1,live::source,(s,x,y,d)->false,live::path,1,100,false,null,angle,
        style,color,false,DiscSettings.ReelColor.DEFAULT,null,0,new RootRenderer.Finishes());
    RootRenderer.paint(cached.scope,1,cached::source,prepared,cached::path,1,100,false,null,angle,
        style,color,false,DiscSettings.ReelColor.DEFAULT,null,0,new RootRenderer.Finishes());
    check(live.pathDraws==1&&cached.pathDraws==0,"prepared vinyl draws no live groove paths");
    check(cached.bitmap.getRGB(600,200)==0,"cached surface preserves the transparent spindle");
    check(cached.sourceCalls==2,"the sleeve and rotating label are still drawn from the cover");
    int totalError=0,samples=0;
    for(int y=20;y<380;y+=9)for(int x=420;x<780;x+=9){
     if(Math.hypot(x-600,y-200)>radius-6)continue;
     var existing=new Color(live.bitmap.getRGB(x,y),true);
     var preparedPixel=new Color(cached.bitmap.getRGB(x,y),true);
     int a=existing.getAlpha(),b=preparedPixel.getAlpha();
     int error=Math.max(Math.abs(a-b),
         Math.max(Math.abs(a*existing.getRed()-b*preparedPixel.getRed()),
             Math.max(Math.abs(a*existing.getGreen()-b*preparedPixel.getGreen()),
                 Math.abs(a*existing.getBlue()-b*preparedPixel.getBlue())))/255);
     totalError+=error;
     samples++;
    }
    // The adapter approximates sweep shaders with aliased wedge and groove paths.
    // Compare premultiplied colors, then check the label and hole exactly.
    check(totalError<5*samples,"cached shading agrees with live vinyl for "+color+" at "+angle+
        " (mean channel error "+(float)totalError/samples+")");
    for(int[] point:new int[][]{{565,165},{635,165},{565,235},{635,235},{600,200}}){
     check(live.bitmap.getRGB(point[0],point[1])==cached.bitmap.getRGB(point[0],point[1]),
         "rotation and resampling leave the label pixels and center hole intact");
    }
    int flank=cached.bitmap.getRGB(600+135,200),upper=cached.bitmap.getRGB(600,200-135);
    if(color==DiscSettings.VinylColor.BLACK)check((upper&255)>(flank&255)+25,"black vinyl retains fixed highlights");
    else check((flank>>>24)>85&&(flank>>>24)<115,"colored vinyl retains translucency");
   }
  }
  System.out.println("PASS prepared vinyl preserves translucency, fixed highlights and label rotation");
 }

}
