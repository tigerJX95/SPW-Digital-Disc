package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.Path;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import com.xuncorp.spw.workshop.api.Channel;
import com.xuncorp.spw.workshop.api.PluginContext;
import java.util.function.Supplier;

public class FirstRevealPreparationTest {
 public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.11.0",".","1.19.3",Channel.MS));
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
  var cdCache=RootRenderer.class.getDeclaredField("cachedFinish");
  cdCache.setAccessible(true);
  Object cdFinish=cdCache.get(null);
  check(cdFinish!=null,"default CD finish is prepared before exposure");
  RootRenderer.paint(raster.scope,1f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,cd,black,false,silver);
  check(cdCache.get(null)==cdFinish,"first CD draw reuses the prepared finish");

  var reel=DiscSettings.Style.REEL;
  prepare.invoke(frame,raster.scope,0f,100,1f,false,reel,black,silver,paths,generatedStatus);
  int reelPaths=raster.masks.size();
  var reelCache=RootRenderer.class.getDeclaredField("cachedReel");
  reelCache.setAccessible(true);
  Object reelFinish=reelCache.get(null);
  check(reelFinish!=null,"reel windows and finish are prepared before exposure");
  RootRenderer.paint(raster.scope,1f,raster::source,(s,x,y,d)->false,paths,.1f,100,false,null,0f,reel,black,false,silver);
  check(raster.masks.size()==reelPaths&&reelCache.get(null)==reelFinish,
      "first reel draw reuses the prepared geometry and finish");
  plugin.stop();
  System.out.println("PASS first reveal: late lookup, style change, trim geometry, custom artwork and hot cache reuse");
 }
 private static void check(boolean valid,String message){
  if(!valid)throw new AssertionError(message);
 }
}
