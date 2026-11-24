package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.drawscope.DrawScope;
import com.xuncorp.spw.workshop.api.Channel;
import com.xuncorp.spw.workshop.api.PluginContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static io.github.tigerjx95.spwcdprobe.DiscSettings.ReelColor.*;

final class ReelEntryRasterTest {
 public static void main(String[] args)throws Exception{
  compareArtwork();
  compareCachedTape();
  checkLateTape();
  retryTemporaryFailure();
  var firstStarted=new CountDownLatch(1);
  var finishFirst=new CountDownLatch(1);
  var reports=new LinkedBlockingQueue<String>();
  var closes=new AtomicInteger();
  var draws=new AtomicInteger();
  var angles=new java.util.ArrayList<Float>();
  try(var cache=new DiscSurfaceRaster<DiscSettings.ReelColor>(color->{
   if(color==SILVER){
    firstStarted.countDown();
    if(!finishFirst.await(3,TimeUnit.SECONDS))throw new AssertionError("test never released first raster");
   }
   return new DiscSurfaceRaster.Raster(){
    public void draw(DrawScope scope,float x,float y,float diameter){draws.incrementAndGet();}
    public void draw(DrawScope scope,float x,float y,float diameter,float angle){
     angles.add(angle);
     draw(scope,x,y,diameter);
    }
    public void close(){closes.incrementAndGet();}
   };
  },reports::offer,System::nanoTime,"REEL_RASTER")){
   cache.prepare(SILVER);
   check(firstStarted.await(3,TimeUnit.SECONDS),"background preparation starts");
   cache.prepare(GOLD);
   finishFirst.countDown();
   String latest=reports.poll(3,TimeUnit.SECONDS);
   check(latest!=null&&latest.startsWith("REEL_RASTER_READY color=GOLD"),"only the requested color publishes");
   check(closes.get()==1&&!cache.ready(SILVER,600),"stale raster closes");
   check(!cache.draw(GOLD,null,300,300,1100),"oversized display retains its live renderer");
   check(cache.draw(GOLD,null,300.25f,300,600)&&draws.get()==1,"matching prepared reel draws");
   checkFrameSelection(cache,angles);
  }
  check(closes.get()==2,"disabling the plugin closes the ready raster");
  System.out.println("PASS solid reel entry raster: fixed lighting and rotating artwork, consistent first reveal, size guard and cleanup");
 }

 private static void checkFrameSelection(DiscSurfaceRaster<DiscSettings.ReelColor> cache,java.util.List<Float> angles)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.12.0",".","1.19.3",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");
  var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
  var frame=constructor.newInstance(plugin);
  var draw=type.getDeclaredMethod("drawPreparedReel",DiscSurfaceRaster.class,DiscSettings.ReelColor.class,
      float.class,DrawScope.class,float.class,float.class,float.class);
  draw.setAccessible(true);
  check((boolean)draw.invoke(frame,cache,GOLD,0f,null,300f,300f,600f),"reveal uses the prepared reel");
  check((boolean)draw.invoke(frame,cache,GOLD,17f,null,300f,300f,600f),"the same raster handles the first nonzero angle");
  check(angles.equals(java.util.List.of(0f,0f,17f)),"the cache receives the exact reel angle");
  check(!(boolean)draw.invoke(frame,cache,SILVER,17f,null,300f,300f,600f),"the previous color cannot render the new reel");
 }

 private static void retryTemporaryFailure()throws Exception{
  var time=new java.util.concurrent.atomic.AtomicLong();
  var attempts=new AtomicInteger();
  var closes=new AtomicInteger();
  var reports=new LinkedBlockingQueue<String>();
  try(var cache=new DiscSurfaceRaster<DiscSettings.ReelColor>(color->{
   int attempt=attempts.incrementAndGet();
   if(attempt==1)throw new IllegalStateException("transient bake failure");
   return new DiscSurfaceRaster.Raster(){
    public void draw(DrawScope scope,float x,float y,float diameter){
     if(attempt==2)throw new IllegalStateException("transient draw failure");
    }
    public void close(){closes.incrementAndGet();}
   };
  },reports::offer,time::get,"REEL_RASTER")){
   cache.prepare(SILVER);
   check(reports.poll(3,TimeUnit.SECONDS).startsWith("REEL_RASTER_FALLBACK"),"bake failure is reported");
   cache.prepare(SILVER);
   check(reports.poll(100,TimeUnit.MILLISECONDS)==null&&attempts.get()==1,"same color waits through retry cooldown");
   time.addAndGet(DiscSurfaceRaster.RETRY_NS);
   cache.prepare(SILVER);
   check(reports.poll(3,TimeUnit.SECONDS).startsWith("REEL_RASTER_READY"),"same color recovers after a temporary bake failure");
   check(!cache.draw(SILVER,null,0,0,600)&&closes.get()==1,"draw failure releases its raster");
   check(reports.poll(3,TimeUnit.SECONDS).startsWith("REEL_RASTER_FALLBACK"),"draw failure is reported");
   time.addAndGet(DiscSurfaceRaster.RETRY_NS);
   cache.prepare(SILVER);
   check(reports.poll(3,TimeUnit.SECONDS).startsWith("REEL_RASTER_READY")&&cache.draw(SILVER,null,0,0,600),
       "same color can recover from a transient draw failure");
  }
  check(attempts.get()==3&&closes.get()==2,"the recovered raster closes exactly once");
  System.out.println("PASS reel raster cooldown and same-color recovery after temporary bake and draw failures");
 }

 private static void compareCachedTape()throws Exception{
  int side=400,radius=196;
  var tape=new RootRasterTest.Raster(side*2,side);
  var paths=new AtomicInteger();
  RootRenderer.bakeReelTape(tape.scope,radius,()->{
   paths.incrementAndGet();
   return tape.path();
  });
  check(paths.get()==2,"tape preparation builds only the two winding paths");
  for(var style:new DiscSettings.Style[]{DiscSettings.Style.REEL,DiscSettings.Style.REEL_COVER}){
   for(float angle:new float[]{0,30}){
    var live=new RootRasterTest.Raster(side*2,side);
    var cached=new RootRasterTest.Raster(side*2,side);
    RootRenderer.Disc disc=new RootRenderer.Disc(){
     public boolean draw(DrawScope scope,float x,float y,float diameter){return false;}
     public boolean drawReelTape(DrawScope scope,float r){
      check(r==radius,"tape draws at the live reel size");
      cached.graphics.drawImage(tape.bitmap,-radius,-radius,null);
      return true;
     }
    };
    RootRenderer.paint(live.scope,1,live::source,(scope,x,y,d)->false,live::path,1,100,false,null,
        angle,style,DiscSettings.VinylColor.BLUE,false,WINE,null,0,new RootRenderer.Finishes());
    RootRenderer.paint(cached.scope,1,cached::source,disc,cached::path,1,100,false,null,
        angle,style,DiscSettings.VinylColor.BLUE,false,WINE,null,0,new RootRenderer.Finishes());
    check(live.pathDraws-cached.pathDraws==2,"cached tape replaces both winding paths at "+angle+"° for "+style);
    check(live.bitmap.getRGB(600,200)==0&&cached.bitmap.getRGB(600,200)==0,"tape preserves the transparent spindle");
    int x=600-Math.round((float)Math.sin(Math.toRadians(angle))*130),y=200+Math.round((float)Math.cos(Math.toRadians(angle))*130);
    var rendered=new java.awt.Color(cached.bitmap.getRGB(x,y),true);
    var original=new java.awt.Color(live.bitmap.getRGB(x,y),true);
    check(rendered.getAlpha()==255&&rendered.getRed()>rendered.getBlue(),"the covered window reveals opaque brown tape");
    check(Math.abs(rendered.getRed()-original.getRed())<=10&&Math.abs(rendered.getGreen()-original.getGreen())<=10
        &&Math.abs(rendered.getBlue()-original.getBlue())<=10,"cached tape retains winding color and shading at "+angle+"°");
   }
  }
  System.out.println("PASS cached tape covers both reel styles during reveal and rotation");
 }

 private static void checkLateTape()throws Exception{
  var reports=new LinkedBlockingQueue<String>();
  var draws=new AtomicInteger();
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.14.0",".","1.19.3",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");
  var constructor=type.getDeclaredConstructors()[0];
  constructor.setAccessible(true);
  var frame=constructor.newInstance(plugin);
  var draw=type.getDeclaredMethod("drawPreparedReelTape",DiscSurfaceRaster.class,DrawScope.class,float.class);
  draw.setAccessible(true);
  try(var cache=new DiscSurfaceRaster<DiscSettings.ReelColor>(color->new DiscSurfaceRaster.Raster(){
   public void draw(DrawScope scope,float x,float y,float diameter){draws.incrementAndGet();}
   public void close(){}
  },reports::offer,System::nanoTime,"REEL_TAPE_RASTER")){
   check(!(boolean)draw.invoke(frame,cache,null,196f),"first draw chooses live tape if the raster is pending");
   cache.prepare(DEFAULT);
   check(reports.poll(3,TimeUnit.SECONDS)!=null&&cache.ready(DEFAULT,392),"the tape raster becomes available");
   check(!(boolean)draw.invoke(frame,cache,null,196f)&&draws.get()==0,
       "a late raster must not replace the live tape during a reveal");
   var next=constructor.newInstance(plugin);
   check((boolean)draw.invoke(next,cache,null,196f)&&(boolean)draw.invoke(next,cache,null,196f)&&draws.get()==2,
       "the next reveal shares one prepared tape for the reel and reflection");
  }
 }

 private static void compareArtwork()throws Exception{
  int side=400,radius=196,diameter=radius*2;
  int left=side*3/2-radius,top=side/2-radius;
  int cx=left+radius,cy=top+radius;
  for(var color:new DiscSettings.ReelColor[]{SILVER,WINE}){
   // The Java2D adapter tessellates sweep gradients using the canvas width.
   // Match its viewport for exact comparisons of the renderer's artwork.
   var fixed=new RootRasterTest.Raster(side*2,side);
   var rotating=new RootRasterTest.Raster(side*2,side);
   fixed.graphics.translate(left,top);
   rotating.graphics.translate(left,top);
   RootRenderer.bakeSolidReel(fixed.scope,rotating.scope,radius,()->{
    var path=fixed.path();
    rotating.masks.put(path,fixed.masks.get(path));
    rotating.outlines.put(path,fixed.outlines.get(path));
    return path;
   },color);
   int fixedLight=fixed.bitmap.getRGB(cx,cy-150);
   for(float angle:new float[]{0,90}){
    var baked=new RootRasterTest.Raster(side*2,side);
    try(var layers=new DiscSurfaceRaster.ReelLayers(layer(fixed,baked,cx,cy),layer(rotating,baked,cx,cy))){
     RootRenderer.paint(baked.scope,1,baked::source,(scope,x,y,d)->{
      layers.draw(scope,x,y,d,angle);
      return true;
     },baked::path,1,100,false,null,angle,DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE,false,color);
    }
    var live=new RootRasterTest.Raster(side*2,side);
    RootRenderer.paint(live.scope,1,live::source,(scope,x,y,d)->false,live::path,1,100,false,null,
        angle,DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE,false,color,null,0,new RootRenderer.Finishes());
    check(baked.saved.isEmpty(),"drawing both layers restores the canvas");
    check(baked.bitmap.getRGB(cx,cy)==0,"spindle remains transparent at "+angle+"°");
    check(baked.bitmap.getRGB(left,top)==0,"corners remain transparent");
    check(baked.bitmap.getRGB(cx,cy-150)==fixedLight,"fixed body lighting does not rotate");
    int tapeX=angle==0?cx:cx-130,tapeY=angle==0?cy+130:cy;
    // Java2D snaps the thin winding strokes differently when rotating a bitmap.
    // Compare the color of the tape patch instead of requiring exact groove pixels.
    var tape=averagePatch(baked.bitmap,tapeX,tapeY);
    var originalTape=averagePatch(live.bitmap,tapeX,tapeY);
    check(tape.getAlpha()==255&&tape.getRed()>tape.getBlue()+12&&maxChannelDelta(tape.getRGB(),originalTape.getRGB())<=8,
        "the brown tape rotates into the same window at "+angle+"°");
    if(angle==0){
     int matches=0,total=0;
     for(int y=24;y<diameter-24;y+=7)for(int x=24;x<diameter-24;x+=7){
      int prepared=baked.bitmap.getRGB(x+left,y+top),original=live.bitmap.getRGB(x+left,y+top);
      if(maxChannelDelta(prepared,original)<=6)matches++;
      total++;
     }
     check(matches>=total*.99,"layering preserves the upright reel for "+color+": "+matches+"/"+total);
    }
   }
  }
 }
 private static java.awt.Color averagePatch(java.awt.image.BufferedImage image,int cx,int cy){
  int red=0,green=0,blue=0,alpha=0;
  for(int y=cy-5;y<=cy+5;y++)for(int x=cx-5;x<=cx+5;x++){
   int pixel=image.getRGB(x,y);
   red+=pixel>>>16&255;
   green+=pixel>>>8&255;
   blue+=pixel&255;
   alpha+=pixel>>>24;
  }
  return new java.awt.Color(red/121,green/121,blue/121,alpha/121);
 }
 private static int maxChannelDelta(int first,int second){
  int largest=0;
  for(int shift=0;shift<=24;shift+=8)
   largest=Math.max(largest,Math.abs((first>>>shift&255)-(second>>>shift&255)));
  return largest;
 }
 private static DiscSurfaceRaster.Raster layer(RootRasterTest.Raster source,RootRasterTest.Raster target,int cx,int cy){
  return new DiscSurfaceRaster.Raster(){
   public void draw(DrawScope scope,float x,float y,float diameter){
    target.graphics.drawImage(source.bitmap,Math.round(x-cx),Math.round(y-cy),null);
   }
   public void close(){}
  };
 }
 private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
