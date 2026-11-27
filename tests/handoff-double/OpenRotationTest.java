package io.github.tigerjx95.spwcdprobe;

import androidx.compose.runtime.MutableState;
import androidx.compose.ui.layout.LayoutCoordinates;
import com.xuncorp.spw.workshop.api.*;
import com.xuncorp.voxzen.ui.component.PlayerSheetState;
import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.*;

import static io.github.tigerjx95.spwcdprobe.ContinuousCoverTest.check;
import static io.github.tigerjx95.spwcdprobe.ContinuousCoverTest.field;

/** Drive the production animation timer with a playing host and no OPEN Canvas callback. */
public class OpenRotationTest {
 public static final OpenRotationTest SERVICE=new OpenRotationTest();
 private boolean playing=true;
 public OpenRotationTest getPlayer(){return this;}
 public boolean isPlaying(){return playing;}

 public static void main(String[] args)throws Exception{
  String song="https://example.invalid/test.flac";
  var media=new PlaybackExtensionPoint.MediaItem("song","artist","album","artist",song,"42",2020,"",0,0,false,true);
  var playback=(WorkshopApi.Playback)Proxy.newProxyInstance(WorkshopApi.class.getClassLoader(),
      new Class[]{WorkshopApi.Playback.class},(p,m,a)->media);
  var previous=WorkshopApi.Companion.instance;
  try{
   WorkshopApi.Companion.setInstance((WorkshopApi)Proxy.newProxyInstance(WorkshopApi.class.getClassLoader(),
       new Class[]{WorkshopApi.class},(p,m,a)->playback));
   var failures=new ArrayList<String>();
   for(var style:DiscSettings.Style.values()){
    try{checkOpen(song,style);}catch(AssertionError error){failures.add(error.getMessage());}
   }
   check(failures.isEmpty(),String.join(", ",failures));
  }finally{WorkshopApi.Companion.instance=previous;}
  System.out.println("PASS production timer starts rotation for every disc style without a final Canvas paint");
 }

 @SuppressWarnings("unchecked")
 private static void checkOpen(String song,DiscSettings.Style style)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.15.0",".","1.19.3",Channel.MS));
  var pluginType=plugin.getClass();
  var frameType=Class.forName(pluginType.getName()+"$Frame");
  var ctor=frameType.getDeclaredConstructors()[0];ctor.setAccessible(true);
  Object frame=ctor.newInstance(plugin);
  var settings=new DiscSettings.Values(100,false,true,new DiscSettings.Appearance(style,DiscSettings.VinylColor.BLUE),
      DiscSpeed.STANDARD,DiscSpeed.STANDARD,LocalDiscService.DEFAULT_NAMES,CoverWhiteBorders.Mode.OFF,Map.of(),Map.of());
  ((MutableState<DiscSettings.Values>)field(pluginType,"displaySettings").get(plugin)).setValue(settings);
  ((MutableState<Boolean>)field(pluginType,"enabled").get(plugin)).setValue(true);
  field(pluginType,"running").setBoolean(plugin,true);
  var binding=field(pluginType,"playbackState").get(plugin);
  field(binding.getClass(),"service").set(binding,OpenRotationTest.class.getField("SERVICE"));
  field(binding.getClass(),"player").set(binding,OpenRotationTest.class.getMethod("getPlayer"));
  field(binding.getClass(),"isPlaying").set(binding,OpenRotationTest.class.getMethod("isPlaying"));
  field(frameType,"secondary").setBoolean(frame,true);
  field(frameType,"sheet").set(frame,new PlayerSheetState(null));
  var coords=Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->{
   if(m.getName().equals("isAttached"))return true;
   if(m.equals(PainterDrawBinding.LAYOUT_SIZE))return 0L;
   throw new AssertionError(m);
  });
  field(frameType,"nativeRoot").set(frame,coords);field(frameType,"widgetRoot").set(frame,coords);
  var sync=frameType.getDeclaredMethod("syncVisibility",boolean.class);sync.setAccessible(true);sync.invoke(frame,true);
  var observe=frameType.getDeclaredMethod("observe",String.class,DiscSettings.AlbumKey.class);observe.setAccessible(true);
  observe.invoke(frame,song,null);
  var pixels=new DiscImage();
  field(frameType,"ownedCover").set(frame,pixels);
  ((MutableState<Object>)field(frameType,"passPainter").get(frame)).setValue(new HostCoverBridge.Decoded(pixels));
  field(frameType,"suppressionApplied").setBoolean(frame,true);
  var frames=(List<WeakReference<Object>>)field(pluginType,"frames").get(plugin);
  frames.add(new WeakReference<>(frame));
  try(var local=new LocalDiscService<DiscImage>(()->song,(bytes,w,h)->new DiscImage(),()->{},msg->{})){
   local.poll();
   field(pluginType,"localDiscs").set(plugin,local);
   var motion=(DiscTransition)field(frameType,"motion").get(frame);
   var timer=pluginType.getDeclaredMethod("animateFrames");timer.setAccessible(true);
   var angle=(MutableState<Float>)field(frameType,"rotationAngle").get(frame);
   long start=System.nanoTime()-2_000_000_000L;
   motion.ready(start);
   // The last OPENING paint can have the same rounded pose as OPEN.
   // No new Canvas pass is delivered during the following timer ticks.
   var advance=frameType.getDeclaredMethod("advanceMotion",long.class);advance.setAccessible(true);
   advance.invoke(frame,start+799_900_000L);
   check(motion.phase()==DiscTransition.Phase.OPENING&&motion.pose().exposed()==1f,
       "last opening pose already rounds to full exposure");
   SERVICE.playing=true;
   timer.invoke(plugin);
   check(motion.phase()==DiscTransition.Phase.OPEN&&angle.getValue()==0,"first open sample starts upright");
   Thread.sleep(16);
   timer.invoke(plugin);
   check(angle.getValue()>0,style+" must start without waiting for another draw");
   SERVICE.playing=false;
   timer.invoke(plugin);
   float paused=angle.getValue();
   timer.invoke(plugin);
   check(angle.getValue()==paused,"pause freezes the advanced angle");
  }
 }
}
