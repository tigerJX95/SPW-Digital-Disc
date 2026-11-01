package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.*;
import androidx.compose.runtime.MutableState;
import androidx.compose.ui.layout.LayoutCoordinates;
import com.xuncorp.spw.workshop.api.*;
import com.xuncorp.voxzen.ui.component.PlayerSheetState;
/** Runs the real production Frame against only native pixels/visibility doubles. */
public class ContinuousCoverTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.1.0-test2",".","1.19.0",Channel.MS));
  ((MutableState<Boolean>)field(CdPageProbePlugin.class,"enabled").get(plugin)).setValue(true);
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  field(type,"secondary").setBoolean(frame,true);field(type,"sheet").set(frame,new PlayerSheetState(null));
  ((MutableState<Boolean>)field(type,"ready").get(frame)).setValue(true);
  LayoutCoordinates root=(LayoutCoordinates)Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->{
   if(m.getName().equals("isAttached"))return true;throw new AssertionError(m);
  });
  field(type,"nativeRoot").set(frame,root);
  var observe=type.getDeclaredMethod("observe",String.class);observe.setAccessible(true);
  var resolve=type.getDeclaredMethod("resolveCover",Object.class);resolve.setAccessible(true);
  var source=type.getDeclaredMethod("source");source.setAccessible(true);
  var suppress=type.getDeclaredMethod("suppress");suppress.setAccessible(true);
  var restore=type.getDeclaredMethod("restoreNative");restore.setAccessible(true);
  var motion=(DiscTransition)field(type,"motion").get(frame);
  var gate=(TrackEntryGate)field(type,"trackGate").get(frame);
  var pass=(MutableState<Object>)field(type,"passPainter").get(frame);
  DiscImage pixels=new DiscImage();Object painter=new HostCoverBridge.Decoded(pixels);
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(painter,root,420*420));
  observe.invoke(frame,"album/0.flac");resolve.invoke(frame,painter);pass.setValue(painter);
  field(type,"suppressionApplied").setBoolean(frame,true);
  long now=1_000_000_000L;motion.enter();motion.ready(now);motion.tick(now+940_000_000L);gate.observe("album/0.flac",now);
  for(int i=1;i<=100;i++){
   String song="album/"+i+".flac";
   observe.invoke(frame,song); // First, the host still exposes the outgoing painter.
   check(field(type,"ownedCover").get(frame)==null,"old cover no longer qualifies as current");
   check(field(type,"pendingCover").get(frame)==pixels&&pixels.closes==0,"already displayed pixels stay alive");
   check(pass.getValue()==pixels&&(Boolean)suppress.invoke(frame),"native suppression never drops during observer-before-draw gap");
   check(resolve.invoke(frame,new Object[]{null})==pixels,"empty incoming catalog keeps same Canvas pixels");
   check(source.invoke(frame)==null&&!HostCoverBridge.imageReady(pass.getValue()),"outgoing painter and placeholder cannot prove new-track readiness");
   check(motion.pose().exposed()==0&&motion.pose().scale()==1&&motion.pose().centered()==0,"waiting cover has stationary full-size geometry and zero CD");
   check(!gate.ready(song,pass.getValue(),HostCoverBridge.imageReady(pass.getValue()),true,Long.MAX_VALUE/2),"even a long wait cannot eject a placeholder");
   DiscImage nextPixels=new DiscImage();Object next=new HostCoverBridge.Decoded(nextPixels);
   HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(painter,root,620*620),new HostCoverBridge.Image(next,root,120*120));
   check(resolve.invoke(frame,source.invoke(frame))==next,"fresh incoming source atomically replaces waiting pixels");
   check(pixels.closes==1&&field(type,"pendingCover").get(frame)==null&&field(type,"ownedCover").get(frame)==nextPixels,"old pixels released exactly once after replacement");
   pass.setValue(next);
   now=System.nanoTime()+300_000_000L;
   check(!gate.ready(song,next,true,true,now)&&gate.ready(song,next,true,true,now+120_000_000L),"fresh cover still obeys existing stable-readiness interval");
   motion.trackReady(now+120_000_000L);motion.tick(now+820_000_000L);
   pixels=nextPixels;painter=next;
   HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(painter,root,420*420));
  }
  observe.invoke(frame,"rapid/B");observe.invoke(frame,"rapid/C");
  check(field(type,"pendingCover").get(frame)==pixels&&pixels.closes==0,"rapid changes retain one bounded waiting image");
  observe.invoke(frame,(String)null);check(pixels.closes==1&&pass.getValue()==null,"no current media releases waiting pixels");
  DiscImage last=new DiscImage();field(type,"pendingCover").set(frame,last);
  restore.invoke(frame);check(last.closes==1&&field(type,"pendingCover").get(frame)==null&&pass.getValue()==null,"page exit releases retained image and restores native layer");
  System.out.println("PASS real production Frame: 100 same-album delayed handoffs without native-layout gap, placeholder cannot eject, stationary cover, fresh-source atomic swap, exact disposal, rapid changes, no-media and page-exit cleanup");
  System.out.println("Native pixels and settled visibility are doubles; native Windows playback is outside this offline test.");
 }
}
