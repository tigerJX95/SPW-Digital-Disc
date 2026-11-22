package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.SkiaBackedCanvas_skikoKt;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.SamplingMode;
import org.jetbrains.skia.Surface;

/** One prepared generated CD, shared by the screen and its reflection. */
final class GeneratedCdRaster implements AutoCloseable {
 private static final int MAX_SIDE=1536;
 record Key(DiscImage cover,CoverWhiteBorders.Bounds crop,float aspect,int size,VinylLabelFraming framing){
  static Key of(DiscImage cover,CoverWhiteBorders.Bounds crop,float aspect,float diameter,VinylLabelFraming framing){
   if(cover==null||!Float.isFinite(diameter)||diameter<=0||diameter>MAX_SIDE)return null;
   return new Key(cover,crop,aspect,(int)Math.ceil(diameter),framing);
  }
 }
 private record Job(Key key,Image pixels){}
 private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
  var thread=new Thread(task,"SPW Digital Disc cover CD preparation");
  thread.setDaemon(true);
  return thread;
 });
 private final Consumer<String> report;
 private Key requested;
 private Job queued;
 private Image ready;
 private boolean active,closed;

 GeneratedCdRaster(Consumer<String> report){this.report=report;}

 synchronized void prepare(Key key){
  if(closed||key==null||key.equals(requested))return;
  clear();
  requested=key;
  try{
   queued=new Job(key,key.cover().snapshot());
  }catch(RuntimeException|LinkageError error){
   report.accept("COVER_CD_RASTER_FALLBACK "+error.getClass().getSimpleName());
   return;
  }
  if(!active){active=true;worker.execute(this::drain);}
 }

 private void drain(){
  for(;;){
   Job next;
   synchronized(this){
    next=queued;
    queued=null;
    if(next==null){active=false;return;}
   }
   long started=System.nanoTime();
   try(Image pixels=next.pixels()){
    Image result=bake(next.key(),pixels);
    synchronized(this){
     if(closed||requested!=next.key()){result.close();continue;}
     ready=result;
    }
    report.accept("COVER_CD_RASTER_READY size="+next.key().size()+" elapsedUs="+(System.nanoTime()-started)/1_000);
   }catch(Exception|LinkageError error){
    report.accept("COVER_CD_RASTER_FALLBACK "+error.getClass().getSimpleName());
   }
  }
 }

 synchronized boolean ready(Key key){return key!=null&&key.equals(requested)&&ready!=null;}

 synchronized boolean draw(Key key,DrawScope scope,float cx,float cy,float diameter){
  if(!ready(key))return false;
  try{
   var canvas=SkiaBackedCanvas_skikoKt.getNativeCanvas(scope.getDrawContext().getCanvas());
   float r=diameter*.5f;
   canvas.drawImageRect(ready,0,0,key.size(),key.size(),cx-r,cy-r,cx+r,cy+r,
       SamplingMode.Companion.getLINEAR(),null,true);
   return true;
  }catch(RuntimeException|LinkageError error){
   ready.close();ready=null;
   report.accept("COVER_CD_RASTER_FALLBACK "+error.getClass().getSimpleName());
   return false;
  }
 }

 synchronized void release(DiscImage cover){
  if(requested!=null&&requested.cover()==cover)clear();
 }

 private void clear(){
  requested=null;
  if(queued!=null){queued.pixels().close();queued=null;}
  if(ready!=null){ready.close();ready=null;}
 }

 @Override public synchronized void close(){
  closed=true;
  clear();
  worker.shutdownNow();
 }

 private static Image bake(Key key,Image pixels)throws Exception{
  try(var surface=Surface.Companion.makeRasterN32Premul(key.size(),key.size())){
   var canvas=surface.getCanvas();
   canvas.clear(0);
   var scope=new CanvasDrawScope();
   var params=scope.getDrawParams();
   params.setCanvas(SkiaBackedCanvas_skikoKt.asComposeCanvas(canvas));
   params.getClass().getMethod("setSize-uvyYCjk",long.class).invoke(params,RootRenderer.pack(key.size(),key.size()));
   var crop=key.crop();
   int left=crop==null?0:crop.x(),top=crop==null?0:crop.y();
   int width=crop==null?pixels.getWidth():crop.width(),height=crop==null?pixels.getHeight():crop.height();
   RootRenderer.Source image=(target,w,h)->canvas.drawImageRect(pixels,left,top,left+width,top+height,
       0,0,Math.max(1,Math.round(w)),Math.max(1,Math.round(h)),SamplingMode.Companion.getLINEAR(),null,true);
   var source=new RootRenderer.Source(){
    @Override public void draw(DrawScope target,float w,float h)throws Exception{image.draw(target,w,h);}
    @Override public RootRenderer.FramedArtwork discArtwork(){
     return key.framing()==null?null:new RootRenderer.FramedArtwork(image,key.aspect(),key.framing());
    }
   };
   RootRenderer.bakeCd(scope,source,key.aspect(),key.size(),SkiaBackedPath_skikoKt::Path);
   return surface.makeImageSnapshot();
  }
 }
}
