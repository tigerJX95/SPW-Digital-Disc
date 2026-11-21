package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.Path;
import androidx.compose.ui.graphics.Paint;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import com.xuncorp.spw.workshop.api.Channel;
import com.xuncorp.spw.workshop.api.PluginContext;
import java.util.function.Supplier;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public class FirstRevealPreparationTest {
 public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.12.0",".","1.19.3",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");
  var ctor=type.getDeclaredConstructor(CdPageProbePlugin.class);
  ctor.setAccessible(true);
  var frame=ctor.newInstance(plugin);
  var prepare=type.getDeclaredMethod("prepareHiddenDisc",DrawScope.class,float.class,int.class,float.class,
      boolean.class,DiscSettings.Style.class,DiscSettings.VinylColor.class,DiscSettings.ReelColor.class,
      Supplier.class,Supplier.class);
  prepare.setAccessible(true);
  var raster=new RootRasterTest.Raster(480,240);
  Supplier<Path> paths=raster::path;
  var pending=new LocalDiscService.ArtworkStatus(false,null);
  var generated=new LocalDiscService.ArtworkStatus(true,null);
  var custom=new LocalDiscService.ArtworkStatus(true,"cd.png");
  Supplier<LocalDiscService.ArtworkStatus> pendingStatus=()->pending;
  Supplier<LocalDiscService.ArtworkStatus> generatedStatus=()->generated;
  Supplier<LocalDiscService.ArtworkStatus> customStatus=()->custom;
  var vinyl=DiscSettings.Style.COLORED_VINYL;
  var red=DiscSettings.VinylColor.RED;
  var blue=DiscSettings.VinylColor.BLUE;
  var silver=DiscSettings.ReelColor.DEFAULT;

  prepare.invoke(frame,raster.scope,0f,100,1f,false,vinyl,red,silver,paths,pendingStatus);
  check(raster.masks.isEmpty(),"pending lookup must wait for a usable result");
  prepare.invoke(frame,raster.scope,0f,100,1f,false,vinyl,red,silver,paths,customStatus);
  check(raster.masks.isEmpty(),"resolved custom image bypasses generated artwork");
  prepare.invoke(frame,raster.scope,0f,0,1f,false,vinyl,red,silver,paths,generatedStatus);
  check(raster.masks.isEmpty(),"zero exposure skips preparation");

  prepare.invoke(frame,raster.scope,0f,100,1f,false,vinyl,red,silver,paths,generatedStatus);
  int preparedPaths=raster.masks.size();
  check(preparedPaths>0,"late lookup prepares the current style while the disc is still hidden");
  RootRenderer.paint(raster.scope,1f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,vinyl,red,false,silver);
  check(raster.masks.size()==preparedPaths,"first reveal reuses the prepared vinyl geometry");

  // Keep the same frame, dimensions and source. A style or color change
  // must prepare the corresponding geometry on the next hidden pass.
  var classic=DiscSettings.Style.BLACK_VINYL;
  var black=DiscSettings.VinylColor.BLACK;
  prepare.invoke(frame,raster.scope,0f,100,1f,false,classic,black,silver,paths,generatedStatus);
  int classicPaths=raster.masks.size();
  check(classicPaths>preparedPaths,"same frame prepares a newly selected style");
  RootRenderer.paint(raster.scope,1f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,classic,black,false,silver);
  check(raster.masks.size()==classicPaths,"first reveal after changing style reuses prepared artwork");

  prepare.invoke(frame,raster.scope,0f,100,2f,true,vinyl,blue,silver,paths,generatedStatus);
  int trimmedPaths=raster.masks.size();
  check(trimmedPaths>classicPaths,"preparation follows a new finish and trimmed cover dimensions");
  RootRenderer.paint(raster.scope,2f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,vinyl,blue,true,silver);
  check(raster.masks.size()==trimmedPaths,"first reveal uses the same radius as the prewarm");
  prepare.invoke(frame,raster.scope,0f,100,2f,true,vinyl,blue,silver,paths,generatedStatus);
  check(raster.masks.size()==trimmedPaths,"repeated hidden frames do not rebuild hot geometry");
  var cd=DiscSettings.Style.CD;
  prepare.invoke(frame,raster.scope,0f,100,1f,false,cd,black,silver,paths,generatedStatus);
  var live=liveFinishes();
  var cdCache=RootRenderer.Finishes.class.getDeclaredField("cd");
  cdCache.setAccessible(true);
  Object cdFinish=cdCache.get(live);
  check(cdFinish!=null,"default CD finish is prepared before exposure");
  RootRenderer.paint(raster.scope,1f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,cd,black,false,silver);
  check(cdCache.get(live)==cdFinish,"first CD draw reuses the prepared finish");

  var reel=DiscSettings.Style.REEL;
  prepare.invoke(frame,raster.scope,0f,100,1f,false,reel,black,silver,paths,generatedStatus);
  int reelPaths=raster.masks.size();
  var reelCache=RootRenderer.Finishes.class.getDeclaredField("reel");
  reelCache.setAccessible(true);
  Object reelFinish=reelCache.get(live);
  check(reelFinish!=null,"reel windows and finish are prepared before exposure");
  RootRenderer.paint(raster.scope,1f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,reel,black,false,silver);
  check(raster.masks.size()==reelPaths&&reelCache.get(live)==reelFinish,
      "first reel draw reuses the prepared geometry and finish");
  plugin.stop();
  verifyDefaultSwitchPreparation();
  System.out.println("PASS first reveal: late lookup, style change, trim geometry, custom artwork and hot cache reuse");
 }

 static RootRenderer.Finishes liveFinishes()throws Exception{
  var field=RootRenderer.class.getDeclaredField("liveFinishes");
  field.setAccessible(true);
  return (RootRenderer.Finishes)field.get(null);
 }

 private static void verifyDefaultSwitchPreparation()throws Exception{
  var config=Files.createTempDirectory("spw-default-style-warmup").resolve("config.json");
  var manager=new DiscSettingsTest.Manager(config,DiscSettingsTest.helper(config));
  var requested=new DiscSettings.Appearance(DiscSettings.Style.COLORED_VINYL,DiscSettings.VinylColor.PURPLE);
  var layout=SelfDrawLayout.fit(1644,1024,1f,true,50);
  var target=RenderWarmup.Target.fromLayout(layout,1f,50);
  var preview=new RootRasterTest.Raster(target.width(),target.height());
  var prepared=new AtomicReference<RootRenderer.Finishes>();
  var current=new AtomicReference<DiscSettings.Appearance>();
  var calls=new AtomicInteger();
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.12.0",config.getParent().toString(),"1.19.3",Channel.MS));
  var pending=CdPageProbePlugin.class.getDeclaredField("preparedAppearance");
  pending.setAccessible(true);
  var preparation=pending.getType().getDeclaredConstructor(DiscSettings.Appearance.class,RootRenderer.Finishes.class);
  preparation.setAccessible(true);
  var apply=CdPageProbePlugin.class.getDeclaredMethod("applyDisplaySettings",DiscSettings.Values.class);
  apply.setAccessible(true);
  var live=liveFinishes();
  var vinylCache=RootRenderer.Finishes.class.getDeclaredField("vinyl");
  vinylCache.setAccessible(true);
  Object before=vinylCache.get(live);
  float[] alpha={1f};
  Paint paint=(Paint)Proxy.newProxyInstance(Paint.class.getClassLoader(),new Class[]{Paint.class},(p,m,a)->switch(m.getName()){
   case "getAlpha"->alpha[0];
   case "setAlpha"->{alpha[0]=(Float)a[0];yield null;}
   default->throw new AssertionError(m);
  });
  try(var settings=new DiscSettings(manager,value->{
   if(requested.equals(value.appearance()))check(prepared.get()!=null,"new appearance cannot reach the UI before its preparation");
   try{apply.invoke(plugin,value);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
   current.set(value.appearance());
  },()->new DiscSettings.ArtworkSource("test"),()->null,appearance->{
   check(!SwingUtilities.isEventDispatchThread(),"changing default styles prepares on the save worker");
   check(requested.equals(appearance)&&!requested.equals(current.get()),"prepare the newly confirmed appearance");
   calls.incrementAndGet();
   try{
    var finishes=RenderWarmup.draw(preview.scope,preview::path,preview::source,new ReflectionRenderer(()->paint),appearance,target);
    prepared.set(finishes);
    pending.set(plugin,preparation.newInstance(appearance,finishes));
    check(vinylCache.get(live)==before,"the worker cannot replace a renderer still in use");
   }catch(Exception error){throw new AssertionError(error);}
  })){
   var dialogField=DiscSettings.class.getDeclaredField("appearanceDialog");
   dialogField.setAccessible(true);
   var dialog=(DiscAppearanceDialog)dialogField.get(settings);
   for(int confirmation=0;confirmation<2;confirmation++){
    var done=new CountDownLatch(1);
    var saved=new AtomicReference<Boolean>();
    SwingUtilities.invokeAndWait(()->DiscAppearanceDialog.runSave(()->dialog.saveDefault(requested),success->{saved.set(success);done.countDown();}));
    check(done.await(5,TimeUnit.SECONDS)&&Boolean.TRUE.equals(saved.get()),"settings confirmation completes");
    DiscSettingsTest.flush();
   }
   check(requested.equals(current.get())&&calls.get()==1,"repeat confirmation does not rewarm the same style");
   check(vinylCache.get(live)==vinylCache.get(prepared.get()),"the UI adopts the exact prepared resources");
   int pathsBefore=preview.masks.size();
   RootRenderer.paint(preview.scope,1f,preview::source,(s,x,y,d)->false,preview::path,.1f,50,false,null,0f,
       requested.style(),requested.finish(),false,requested.reelColor());
   check(preview.masks.size()==pathsBefore,"first visible frame reuses the warm finish at the expected Canvas size");
  }
  plugin.stop();
  System.out.println("PASS default style confirmation prepares off the UI thread before an immediate reveal");
 }
 private static void check(boolean valid,String message){
  if(!valid)throw new AssertionError(message);
 }
}
