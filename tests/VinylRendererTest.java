package io.github.tigerjx95.spwcdprobe;

import java.awt.Color;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
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
  var shadowOpacity=new EnumMap<DiscSettings.VinylColor,Integer>(DiscSettings.VinylColor.class);
  for(var color:DiscSettings.VinylColor.values()){
   if(color==DiscSettings.VinylColor.RANDOM)continue;
   var sample=new RootRasterTest.Raster(800,400);
   RootRenderer.paint(sample.scope,2,sample::source,(s,x,y,d)->false,sample::path,1,100,false,null,90,true,color);
   check(palettePixels.add(sample.bitmap.getRGB(600,65)),"each color refreshes the cached finish");
   if(color!=DiscSettings.VinylColor.BLACK){
    int ring=sample.bitmap.getRGB(680,200);
    check(ringPixels.add(ring),"runout has a distinct tint for "+color);
    ringOpacities.add(ring>>>24);
    // Sample the bright runout at the same radius and phase as the test.4.5 reference.
    int original=switch(color){
     case BLUE -> 0xac2eb3d5;
     case GREEN -> 0xaa57ba87;
     case AMBER -> 0xa9d2a144;
     case PURPLE -> 0xabb68edd;
     case PINK -> 0xa9df8bb1;
     case RED -> 0xafdf767f;
     default -> throw new AssertionError(color);
    };
    for(int shift=0;shift<32;shift+=8)
     check(Math.abs((ring>>>shift&255)-(original>>>shift&255))<=2,"original runout pigment and opacity for "+color);
    checkTransmission(sample,color);
    int face=sample.bitmap.getRGB(735,200);
    var tint=new Color(face,true);
    int high=Math.max(tint.getRed(),Math.max(tint.getGreen(),tint.getBlue()));
    int low=Math.min(tint.getRed(),Math.min(tint.getGreen(),tint.getBlue()));
    check(tint.getAlpha()>85&&tint.getAlpha()<120&&high-low>=40,"colored shadows retain tint and translucency for "+color);
    shadowOpacity.put(color,tint.getAlpha());
   }
   if(color==DiscSettings.VinylColor.PINK){
    int ring=sample.bitmap.getRGB(680,200);
    int face=sample.bitmap.getRGB(735,200);
    int red=ring>>>16&255,green=ring>>>8&255,blue=ring&255;
    check(red>green+28&&blue>green&&red>(face>>>16&255),"runout is a brighter, more saturated pink");
    check((ring>>>24)>160&&(ring>>>24)<180,"pink runout remains translucent");
   }
   check(sample.sourceCalls==2&&sample.bitmap.getRGB(600,200)==0,"colored vinyl keeps cover label and transparent hole");
  }
  check(Collections.max(ringOpacities)-Collections.min(ringOpacities)<=8,"colored runouts retain similar opacity across the palette");
  check(shadowOpacity.get(DiscSettings.VinylColor.RED)>shadowOpacity.get(DiscSettings.VinylColor.AMBER)+5
      &&shadowOpacity.get(DiscSettings.VinylColor.RED)>shadowOpacity.get(DiscSettings.VinylColor.PINK)+5,
      "the deeper red pigment holds its color over a bright background");
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
  check((overWhite>>>24)==255&&Math.abs((overWhite>>16&255)-(body>>16&255)*(body>>>24)/255-(255-(body>>>24)))<=2,"light background remains visible through the colored material");
  var custom=new RootRasterTest.Raster(800,400);
  RootRenderer.paint(custom.scope,1,custom::source,(s,x,y,d)->{
   custom.graphics.setColor(Color.MAGENTA);
   custom.graphics.fill(new Rectangle2D.Float(x-10,y-10,20,20));return true;
  },custom::path,1,100,false,null,0,true,DiscSettings.VinylColor.BLUE);
  check(custom.sourceCalls==1&&custom.bitmap.getRGB(600,200)==Color.MAGENTA.getRGB(),"custom artwork takes priority");
  checkFramedLabel();
  comparePreparedSurface();
  checkBlackGroovesAtMultipleSizes();
  checkColoredVinylGrooves();
  System.out.println("PASS vinyl label crop, dark outer grooves, spindle transparency, restored Canvas and custom priority");
 }
 private static void checkTransmission(RootRasterTest.Raster sample,DiscSettings.VinylColor color){
  int sum=0,count=0;
  for(int y=0;y<400;y+=3)for(int x=400;x<800;x+=3){
   double radius=Math.hypot(x+.5-600,y+.5-200)/196;
   if(radius>.50&&radius<.95){sum+=sample.bitmap.getRGB(x,y)>>>24;count++;}
  }
  double transmittance=1-sum/(255.*count);
  check(transmittance>.45&&transmittance<.61,"translucent test.4.5 base remains visible through the groove field for "+color);
  int lip=sample.bitmap.getRGB(795,200),body=sample.bitmap.getRGB(735,200);
  check((lip>>>24)>(body>>>24),"the original tinted rim is distinct from the body");
  // Sample inside the ring and all the way through its taper, using the test.4.5 render.
  int[] reference=switch(color){
    case BLUE -> new int[]{0xab2eb3d5,0x992bafcf,0x8526a7c5,0x7625a2c0,0x6c219cb8};
    case GREEN -> new int[]{0xaa56bc86,0x9753b682,0x824eb27f,0x7349ab7a,0x6946a875};
    case AMBER -> new int[]{0xa8d1a144,0x95cf9f44,0x80cb9b42,0x71c99c41,0x67c99940};
    case PURPLE -> new int[]{0xabb68cdd,0x98b288d7,0x84ac83cf,0x75a67cc9,0x6ba07ac1};
    case PINK -> new int[]{0xa8df8cb0,0x95db8baf,0x80d389ab,0x71cd8aa9,0x67cb88a8};
    case RED -> new int[]{0xaedf777e,0x9dd86e7a,0x89cd6572,0x7bc55b6c,0x72bc5367};
    default -> throw new AssertionError(color);
  };
  int[] xPositions={681,684,687,690,692};
  for(int i=0;i<xPositions.length;i++){
   int actual=sample.bitmap.getRGB(xPositions[i],200);
   for(int shift=0;shift<32;shift+=8)
    check(Math.abs((actual>>>shift&255)-(reference[i]>>>shift&255))<=1,
        "runout pigment and transmission match test.4.5 at "+xPositions[i]+" for "+color);
  }
 }
 private static void checkBlackGroovesAtMultipleSizes()throws Exception{
  int previousCount=0;
  for(int side:new int[]{180,295,420,620}){
   var upright=vinyl(side,0,DiscSettings.VinylColor.BLACK);
   int count=maxPathSubpaths(upright);
   for(int y=0;y<side;y++)for(int x=side;x<side*2;x++){
    double r=Math.hypot(x+.5-side*1.5,y+.5-side*.5)/(side*.49);
    if(r>.39&&r<.99)check((upright.bitmap.getRGB(x,y)>>>24)==255,"black vinyl body blocks the backdrop");
   }
   check(count>previousCount&&count<=48,"smaller discs draw fewer rings and the upper bound is 48");
   check(side*.49f*.58f/count>=2.59f,"grooves avoid subpixel crowding");
   previousCount=count;
   for(float angle:new float[]{23,67,133,271}){
    var rotated=vinyl(side,angle,DiscSettings.VinylColor.BLACK);
    checkStationarySurface(upright,rotated,side);
    rotated.graphics.dispose();
   }
   upright.graphics.dispose();
  }
  System.out.println("PASS classic vinyl groove spacing and stationary surface across four sizes and rotation angles");
 }
 private static void checkColoredVinylGrooves()throws Exception{
  for(var color:DiscSettings.VinylColor.values()){
   if(color==DiscSettings.VinylColor.BLACK||color==DiscSettings.VinylColor.RANDOM)continue;
   for(int radius:new int[]{88,196,332,512})checkFirstGroove(color,radius);
   var small=vinyl(180,0,color);
   var large=vinyl(620,0,color);
   check(maxPathSubpaths(small)<maxPathSubpaths(large)&&maxPathSubpaths(large)>42&&maxPathSubpaths(large)<=80,
       "the colored groove bands thin out on small surfaces for "+color);
   for(float angle:new float[]{37,133}){
    var rotated=vinyl(180,angle,color);
    checkStationarySurface(small,rotated,180);
    rotated.graphics.dispose();
   }
   small.graphics.dispose();
   large.graphics.dispose();
  }
  var random=DiscSettings.VinylColor.RANDOM;
  var resolved=java.util.EnumSet.noneOf(DiscSettings.VinylColor.class);
  for(int album=0;album<128;album++){
   String title="Album "+album;
   var first=random.resolve(title,"Artist",null,2026,"a.flac",14);
   var second=random.resolve(title,"Artist",null,2026,"b.flac",14);
   check(first==second,"random color stays fixed for an album");
   resolved.add(first);
  }
  check(resolved.size()==6&&!resolved.contains(DiscSettings.VinylColor.BLACK)&&!resolved.contains(random),
      "random mode uses exactly the six original pigments");
  System.out.println("PASS colored vinyl groove density, stationary reflections and the six-color random palette");
 }
 private static void checkFirstGroove(DiscSettings.VinylColor color,int radius)throws Exception{
  class GrooveProbe extends RootRasterTest.Raster{
   int walls;
   GrooveProbe()throws Exception{super(2*radius,2*radius);}
   @Override void sweep(androidx.compose.ui.graphics.SweepGradient brush,java.awt.Shape shape){
    if(shape instanceof java.awt.geom.Ellipse2D)return; // Base pigment has no stroke.
    walls++;
    double width=Math.max(.72,radius*.0033);
    double innerEdge=.478*radius-width*(walls==1?.76+1.08/2:.5);
    check(!shape.intersects(0,-.02,innerEdge-.06,.04),"groove extends into the original runout for "+color);
    check(shape.intersects(innerEdge,-.02,.12,.04),"first groove meets the original runout for "+color);
   }
  }
  var probe=new GrooveProbe();
  RootRenderer.bakeVinylSurface(probe.scope,radius,probe::path,color);
  check(probe.walls==2,"checked both lit and shaded groove walls");
  probe.graphics.dispose();
 }
 private static void checkStationarySurface(RootRasterTest.Raster upright,RootRasterTest.Raster rotated,int side){
  int changedLabel=0;
  double cx=side*1.5,cy=side*.5,radius=side*.49;
  for(int y=0;y<side;y++)for(int x=side;x<side*2;x++){
   double distance=Math.hypot(x+.5-cx,y+.5-cy);
   int first=upright.bitmap.getRGB(x,y),second=rotated.bitmap.getRGB(x,y);
   if(distance>radius*.39)check(first==second,"grooves, highlights and silhouette keep the same pixels at every angle");
   if(distance>radius*.05&&distance<radius*.36&&first!=second)changedLabel++;
  }
  check(changedLabel>100,"cover label still rotates");
 }
 private static RootRasterTest.Raster vinyl(int side,float angle,DiscSettings.VinylColor color)throws Exception{
  var raster=new RootRasterTest.Raster(side*2,side);
  var style=color==DiscSettings.VinylColor.BLACK?DiscSettings.Style.BLACK_VINYL:DiscSettings.Style.COLORED_VINYL;
  RootRenderer.paint(raster.scope,1,(scope,w,h)->{
   raster.graphics.setColor(Color.BLUE);
   raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
   raster.graphics.setColor(Color.YELLOW);
   raster.graphics.fill(new Rectangle2D.Float(0,0,w*.5f,h));
  },(scope,x,y,d)->false,raster::path,1,100,false,null,angle,
      style,color,false,DiscSettings.ReelColor.DEFAULT,null,0,new RootRenderer.Finishes());
  check(raster.pathDraws==2&&raster.saved.isEmpty(),"only the expected live groove paths are drawn");
  return raster;
 }
 private static int maxPathSubpaths(RootRasterTest.Raster raster){
  int max=0;
  for(var path:raster.outlines.values()){
   int count=0;
   var coords=new float[6];
   for(var segments=path.getPathIterator(null);!segments.isDone();segments.next())
    if(segments.currentSegment(coords)==PathIterator.SEG_MOVETO)count++;
   max=Math.max(max,count);
  }
  return max;
 }
 private static void checkFramedLabel()throws Exception{
  var raster=new RootRasterTest.Raster(800,400);
  var source=new RootRenderer.Source(){
   @Override public void draw(androidx.compose.ui.graphics.drawscope.DrawScope s,float w,float h){
    raster.graphics.setColor(Color.RED);
    raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
   }
   @Override public RootRenderer.FramedArtwork vinylLabel(){
    return new RootRenderer.FramedArtwork((s,w,h)->{
     raster.graphics.setColor(Color.WHITE);
     raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
    },.5f,new VinylLabelFraming(VinylLabelFraming.Crop.FULL,true,100,0,0));
   }
  };
  var color=DiscSettings.VinylColor.BLUE;
  var finish=new RootRenderer.Finishes();
  RootRenderer.paint(raster.scope,1,source,(s,x,y,d)->false,raster::path,1,100,false,null,90,
      DiscSettings.Style.COLORED_VINYL,color,false,DiscSettings.ReelColor.DEFAULT,null,0,finish);
  check(raster.bitmap.getRGB(200,200)==Color.RED.getRGB(),"sleeve still draws the original cover");
  check(raster.bitmap.getRGB(650,200)==Color.WHITE.getRGB(),"portrait label rotates as a unit");
  check(raster.bitmap.getRGB(600,248)==color.shade(65),"open label space is filled with the disc color");
  check(raster.bitmap.getRGB(600,200)==0,"framed labels keep the transparent spindle");
  RootRenderer.paint(raster.scope,1,source,(s,x,y,d)->false,raster::path,1,100,false,null,0,
      DiscSettings.Style.BLACK_VINYL,DiscSettings.VinylColor.BLACK,false,DiscSettings.ReelColor.DEFAULT,null,0,finish);
  check(raster.bitmap.getRGB(600,248)==Color.WHITE.getRGB(),"classic vinyl can use the independent portrait label");
  check(raster.bitmap.getRGB(650,200)==RootRenderer.BLACK_LABEL_COLOR,"classic vinyl fills uncovered label with black");
 }
 private static void comparePreparedSurface()throws Exception{
  int side=400,radius=196;
  for(var color:DiscSettings.VinylColor.values()){
   if(color==DiscSettings.VinylColor.RANDOM)continue;
   var surface=new RootRasterTest.Raster(side*2,side);
   // Match the live pixel origin for the Java2D sweep wedge rasterizer.
   surface.graphics.translate(600-radius,200-radius);
   RootRenderer.bakeVinylSurface(surface.scope,radius,surface::path,color);
   if(color!=DiscSettings.VinylColor.BLACK)
    check(surface.circles==2,"colored surface draws only the body and tint, without spotlights");
   for(float angle:new float[]{0,37,90}){
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
    check(live.pathDraws==2&&cached.pathDraws==0,"prepared vinyl draws no live groove paths");
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
    else check((flank>>>24)>85&&(flank>>>24)<120,"colored vinyl retains translucency");
   }
  }
  System.out.println("PASS prepared vinyl preserves translucency, fixed highlights and label rotation");
 }

}
