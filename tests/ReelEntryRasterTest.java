package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static io.github.tigerjx95.spwcdprobe.DiscSettings.ReelColor.*;

final class ReelEntryRasterTest {
 public static void main(String[] args)throws Exception{
  compareArtwork();
  var firstStarted=new CountDownLatch(1);
  var finishFirst=new CountDownLatch(1);
  var reports=new LinkedBlockingQueue<String>();
  var closes=new AtomicInteger();
  var draws=new AtomicInteger();
  try(var cache=new ReelEntryRaster(color->{
   if(color==SILVER){
    firstStarted.countDown();
    if(!finishFirst.await(3,TimeUnit.SECONDS))throw new AssertionError("test never released first raster");
   }
   return new ReelEntryRaster.Raster(){
    public void draw(DrawScope scope,float x,float y,float diameter){draws.incrementAndGet();}
    public void close(){closes.incrementAndGet();}
   };
  },reports::offer)){
   cache.prepare(SILVER);
   check(firstStarted.await(3,TimeUnit.SECONDS),"background preparation starts");
   cache.prepare(GOLD);
   finishFirst.countDown();
   String latest=reports.poll(3,TimeUnit.SECONDS);
   check(latest!=null&&latest.startsWith("REEL_RASTER_READY color=GOLD"),"only the requested color publishes");
   check(closes.get()==1&&!cache.ready(SILVER,600),"stale raster closes");
   check(!cache.draw(GOLD,null,300,300,1100),"oversized display retains its live renderer");
   check(cache.draw(GOLD,null,300.25f,300,600)&&draws.get()==1,"matching prepared reel draws");
  }
  check(closes.get()==2,"disabling the plugin closes the ready raster");
  System.out.println("PASS solid reel entry raster: same artwork and spindle, stale-color protection, size guard and cleanup");
 }

 private static void compareArtwork()throws Exception{
  int side=400,radius=196,diameter=radius*2;
  int left=side*3/2-radius,top=side/2-radius;
  for(var color:new DiscSettings.ReelColor[]{SILVER,WINE}){
   // The Java2D adapter tessellates sweep gradients using the canvas width.
   // Match its viewport for exact comparisons of the renderer's artwork.
   var baked=new RootRasterTest.Raster(side*2,side);
   baked.graphics.translate(left,top);
   RootRenderer.bakeSolidReel(baked.scope,radius,baked::path,color);
   var live=new RootRasterTest.Raster(side*2,side);
   RootRenderer.paint(live.scope,1,live::source,(scope,x,y,d)->false,live::path,1,100,false,null,
       0,DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE,false,color);
   check((baked.bitmap.getRGB(left+radius,top+radius)>>>24)==0,"spindle remains transparent");
   check((baked.bitmap.getRGB(left,top)>>>24)==0,"corners remain transparent");
   for(int y=24;y<diameter-24;y+=7)for(int x=24;x<diameter-24;x+=7)
    check(baked.bitmap.getRGB(x+left,y+top)==live.bitmap.getRGB(x+left,y+top),
        "new raster uses the upright live reel design at "+x+","+y+" for "+color);
  }
 }
 private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
