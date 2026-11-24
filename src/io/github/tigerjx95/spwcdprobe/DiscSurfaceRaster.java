package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.SkiaBackedCanvas_skikoKt;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.jetbrains.skia.FilterMipmap;
import org.jetbrains.skia.FilterMode;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.MipmapMode;
import org.jetbrains.skia.SamplingMode;
import org.jetbrains.skia.Surface;

/** One background-rendered surface. The worker never reads the host Canvas. */
final class DiscSurfaceRaster<C extends Enum<C>> implements AutoCloseable {
 static final int SIZE=1024;
 static final long RETRY_NS=5_000_000_000L;
 interface Raster extends AutoCloseable {
  void draw(DrawScope scope,float x,float y,float diameter)throws Exception;
  default void draw(DrawScope scope,float x,float y,float diameter,float angle)throws Exception{
   draw(scope,x,y,diameter);
  }
  @Override void close();
 }
 interface Factory<C> {Raster prepare(C color)throws Exception;}
 private interface Painter {void draw(DrawScope scope)throws Exception;}
 private record Request<C>(C color){}
 private final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1,task->{
  var thread=new Thread(task,"SPW Digital Disc surface preparation");
  thread.setDaemon(true);
  return thread;
 });
 private final Factory<C> factory;
 private final Consumer<String> report;
 private final LongSupplier clock;
 private final String logName;
 private final IdentityHashMap<C,Long> retryAt=new IdentityHashMap<>();
 private Request<C> requested;
 private Future<?> pending;
 private C readyColor;
 private Raster ready;
 private boolean closed;

 static DiscSurfaceRaster<DiscSettings.ReelColor> reel(Consumer<String> report){
  return new DiscSurfaceRaster<>(DiscSurfaceRaster::bakeReel,report,System::nanoTime,"REEL_RASTER");
 }

 static DiscSurfaceRaster<DiscSettings.ReelColor> tape(Consumer<String> report){
  return new DiscSurfaceRaster<>(color->bakeImage(scope->
      RootRenderer.bakeReelTape(scope,SIZE*.5f,SkiaBackedPath_skikoKt::Path)),
      report,System::nanoTime,"REEL_TAPE_RASTER");
 }

 static DiscSurfaceRaster<DiscSettings.VinylColor> vinyl(Consumer<String> report){
  return new DiscSurfaceRaster<>(color->bakeImage(scope->
      RootRenderer.bakeVinylSurface(scope,SIZE*.5f,SkiaBackedPath_skikoKt::Path,color)),
      report,System::nanoTime,"VINYL_SURFACE_RASTER");
 }

 DiscSurfaceRaster(Factory<C> factory,Consumer<String> report,LongSupplier clock,String logName){
  this.factory=factory;
  this.logName=logName;
  this.report=report;
  this.clock=clock;
  worker.setRemoveOnCancelPolicy(true);
 }

 synchronized void prepare(C color){
  if(closed||requested!=null&&requested.color()==color)return;
  if(pending!=null)pending.cancel(false);
  pending=null;requested=null;
  Long retry=retryAt.get(color);
  if(readyColor==color||retry!=null&&clock.getAsLong()-retry<0)return;
  Request<C> request=new Request<>(color);
  requested=request;
  pending=worker.submit(()->prepare(request));
 }

 private void prepare(Request<C> request){
  long started=System.nanoTime();
  try{
   Raster raster=Objects.requireNonNull(factory.prepare(request.color()));
   synchronized(this){
    if(closed||requested!=request){raster.close();return;}
    if(ready!=null)ready.close();
    ready=raster;
    readyColor=request.color();
    retryAt.remove(readyColor);
    requested=null;pending=null;
   }
   report.accept(logName+"_READY color="+request.color()+" elapsedUs="+(System.nanoTime()-started)/1_000);
  }catch(Exception|LinkageError unavailable){
   synchronized(this){
    if(closed||requested!=request)return;
    retryAt.put(request.color(),clock.getAsLong()+RETRY_NS);
    requested=null;pending=null;
   }
   report.accept(logName+"_FALLBACK "+unavailable.getClass().getSimpleName());
  }
 }

 synchronized boolean ready(C color,float diameter){
  return !closed&&readyColor==color&&ready!=null&&diameter>0&&diameter<=SIZE;
 }

 synchronized boolean draw(C color,DrawScope scope,float x,float y,float diameter){
  return draw(color,scope,x,y,diameter,0);
 }

 synchronized boolean draw(C color,DrawScope scope,float x,float y,float diameter,float angle){
  if(!ready(color,diameter))return false;
  try{ready.draw(scope,x,y,diameter,angle);return true;}
  catch(Exception|LinkageError unavailable){
   ready.close();ready=null;readyColor=null;
   retryAt.put(color,clock.getAsLong()+RETRY_NS);
   report.accept(logName+"_FALLBACK "+unavailable.getClass().getSimpleName());
   return false;
  }
 }

 @Override public synchronized void close(){
  closed=true;
  worker.shutdownNow();
  requested=null;pending=null;retryAt.clear();
  if(ready!=null){ready.close();ready=null;}
  readyColor=null;
 }

 private static Raster bakeReel(DiscSettings.ReelColor color)throws Exception{
  try(var fixed=Surface.Companion.makeRasterN32Premul(SIZE,SIZE);
      var rotating=Surface.Companion.makeRasterN32Premul(SIZE,SIZE)){
   RootRenderer.bakeSolidReel(rasterScope(fixed),rasterScope(rotating),SIZE*.5f,SkiaBackedPath_skikoKt::Path,color);
   var base=new ImageRaster(fixed.makeImageSnapshot(),false);
   try{return new ReelLayers(base,new ImageRaster(rotating.makeImageSnapshot(),true));}
   catch(RuntimeException|LinkageError unavailable){base.close();throw unavailable;}
  }
 }

 private static Raster bakeImage(Painter painter)throws Exception{
  try(var surface=Surface.Companion.makeRasterN32Premul(SIZE,SIZE)){
   painter.draw(rasterScope(surface));
   return new ImageRaster(surface.makeImageSnapshot(),true);
  }
 }

 private static CanvasDrawScope rasterScope(Surface surface)throws Exception{
  surface.getCanvas().clear(0);
  var scope=new CanvasDrawScope();
  var params=scope.getDrawParams();
  params.setCanvas(SkiaBackedCanvas_skikoKt.asComposeCanvas(surface.getCanvas()));
  params.getClass().getMethod("setSize-uvyYCjk",long.class).invoke(params,RootRenderer.pack(SIZE,SIZE));
  return scope;
 }

 record ReelLayers(Raster fixed,Raster rotating) implements Raster {
  @Override public void draw(DrawScope scope,float x,float y,float diameter)throws Exception{
   draw(scope,x,y,diameter,0);
  }
  @Override public void draw(DrawScope scope,float x,float y,float diameter,float angle)throws Exception{
   // The caller has already rotated the reel. Undo it only for the fixed lighting.
   var canvas=scope.getDrawContext().getCanvas();
   canvas.save();
   try{
    if(angle!=0){
     canvas.translate(x,y);
     canvas.rotate(-angle);
     canvas.translate(-x,-y);
    }
    fixed.draw(scope,x,y,diameter);
   }finally{canvas.restore();}
   rotating.draw(scope,x,y,diameter);
  }
  @Override public void close(){
   try{fixed.close();}finally{rotating.close();}
  }
 }

 private record ImageRaster(Image image,boolean mipmapped) implements Raster {
  private static final SamplingMode MINIFICATION=new FilterMipmap(FilterMode.LINEAR,MipmapMode.LINEAR);
  @Override public void draw(DrawScope scope,float x,float y,float diameter){
   var canvas=SkiaBackedCanvas_skikoKt.getNativeCanvas(scope.getDrawContext().getCanvas());
   float radius=diameter*.5f;
   // The whole image is the source. Strict subrect sampling disables mipmaps.
   canvas.drawImageRect(image,0,0,SIZE,SIZE,x-radius,y-radius,x+radius,y+radius,
       mipmapped?MINIFICATION:SamplingMode.Companion.getLINEAR(),null,!mipmapped);
  }
  @Override public void close(){image.close();}
 }
}
