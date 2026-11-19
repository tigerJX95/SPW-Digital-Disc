package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.SkiaBackedCanvas_skikoKt;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.SamplingMode;
import org.jetbrains.skia.Surface;

/** One background-rendered solid reel. The worker never reads the host Canvas. */
final class ReelEntryRaster implements AutoCloseable {
 static final int SIZE=1024;
 interface Raster extends AutoCloseable {
  void draw(DrawScope scope,float x,float y,float diameter)throws Exception;
  @Override void close();
 }
 interface Factory {Raster prepare(DiscSettings.ReelColor color)throws Exception;}
 private record Request(DiscSettings.ReelColor color){}
 private final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1,task->{
  var thread=new Thread(task,"SPW Digital Disc reel preparation");
  thread.setDaemon(true);
  return thread;
 });
 private final Factory factory;
 private final Consumer<String> report;
 private Request requested;
 private Future<?> pending;
 private DiscSettings.ReelColor readyColor;
 private Raster ready;
 private boolean closed;

 ReelEntryRaster(Consumer<String> report){this(ReelEntryRaster::bake,report);}

 ReelEntryRaster(Factory factory,Consumer<String> report){
  this.factory=factory;
  this.report=report;
  worker.setRemoveOnCancelPolicy(true);
 }

 synchronized void prepare(DiscSettings.ReelColor color){
  if(closed||requested!=null&&requested.color()==color)return;
  Request request=new Request(color);
  requested=request;
  if(pending!=null)pending.cancel(false);
  if(readyColor==color){pending=null;return;}
  pending=worker.submit(()->prepare(request));
 }

 private void prepare(Request request){
  long started=System.nanoTime();
  try{
   Raster raster=factory.prepare(request.color());
   synchronized(this){
    if(closed||requested!=request){raster.close();return;}
    if(ready!=null)ready.close();
    ready=raster;
    readyColor=request.color();
   }
   report.accept("REEL_RASTER_READY color="+request.color()+" elapsedUs="+(System.nanoTime()-started)/1_000);
  }catch(Exception|LinkageError unavailable){
   report.accept("REEL_RASTER_FALLBACK "+unavailable.getClass().getSimpleName());
  }
 }

 synchronized boolean ready(DiscSettings.ReelColor color,float diameter){
  return !closed&&readyColor==color&&ready!=null&&diameter>0&&diameter<=SIZE;
 }

 synchronized boolean draw(DiscSettings.ReelColor color,DrawScope scope,float x,float y,float diameter){
  if(!ready(color,diameter))return false;
  try{ready.draw(scope,x,y,diameter);return true;}
  catch(Exception|LinkageError unavailable){
   ready.close();ready=null;readyColor=null;
   report.accept("REEL_RASTER_FALLBACK "+unavailable.getClass().getSimpleName());
   return false;
  }
 }

 @Override public synchronized void close(){
  closed=true;
  worker.shutdownNow();
  if(ready!=null){ready.close();ready=null;}
  readyColor=null;
 }

 private static Raster bake(DiscSettings.ReelColor color)throws Exception{
  Image image;
  try(var surface=Surface.Companion.makeRasterN32Premul(SIZE,SIZE)){
   surface.getCanvas().clear(0);
   var scope=new CanvasDrawScope();
   var params=scope.getDrawParams();
   params.setCanvas(SkiaBackedCanvas_skikoKt.asComposeCanvas(surface.getCanvas()));
   params.getClass().getMethod("setSize-uvyYCjk",long.class).invoke(params,RootRenderer.pack(SIZE,SIZE));
   RootRenderer.bakeSolidReel(scope,SIZE*.5f,SkiaBackedPath_skikoKt::Path,color);
   image=surface.makeImageSnapshot();
  }
  return new Raster(){
   public void draw(DrawScope scope,float x,float y,float diameter){
    var canvas=SkiaBackedCanvas_skikoKt.getNativeCanvas(scope.getDrawContext().getCanvas());
    float radius=diameter*.5f;
    canvas.drawImageRect(image,0,0,SIZE,SIZE,x-radius,y-radius,x+radius,y+radius,
        SamplingMode.Companion.getLINEAR(),null,true);
   }
   public void close(){image.close();}
  };
 }
}
