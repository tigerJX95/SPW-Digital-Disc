package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.*;
import androidx.compose.runtime.MutableState;
import androidx.compose.ui.layout.LayoutCoordinates;
import com.xuncorp.spw.workshop.api.*;
public class SourceSwitchTest {
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 @SuppressWarnings("unchecked")
 public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","0.4.8.4",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  boolean[] attached={true};
  LayoutCoordinates root=(LayoutCoordinates)Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->switch(m.getName()){
   case "isAttached"->attached[0];case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);default->throw new AssertionError(m);
  });
  var rootField=type.getDeclaredField("nativeRoot");rootField.setAccessible(true);rootField.set(frame,root);
  var source=type.getDeclaredMethod("source");source.setAccessible(true);
  var artworkField=type.getDeclaredField("artwork");artworkField.setAccessible(true);MutableState<Object> artwork=(MutableState<Object>)artworkField.get(frame);
  var rejectedField=type.getDeclaredField("rejectedSource");rejectedField.setAccessible(true);MutableState<Object> rejected=(MutableState<Object>)rejectedField.get(frame);
  Object previous=new Object();artwork.setValue(previous);
  for(int i=0;i<100;i++){
   Object next=new Object();HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(next,root,420*420)));
   check(source.invoke(frame)==next,"fresh source found despite stale captured painter");
   artwork.setValue(next);check(source.invoke(frame)==next,"capture adopts current source");
   previous=next;
  }
  Object temporary=new Object();
  HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(temporary,root,420*420)));
  artwork.setValue(temporary);check(source.invoke(frame)==temporary,"temporary sole native node takes over");
  HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(previous,root,420*420)));
  check(source.invoke(frame)==previous,"production Frame recovers original live source after temporary node disappears");
  artwork.setValue(previous);
  rejected.setValue(previous);check(source.invoke(frame)==null,"closed/rejected source skipped for native fallback");
  Object fresh=new Object();HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(fresh,root,420*420)));
  check(source.invoke(frame)==fresh,"new track recovers after old source failure");
  attached[0]=false;check(source.invoke(frame)==null,"detached page cannot supply stale source");
  attached[0]=true;
  var secondary=type.getDeclaredField("secondary");secondary.setAccessible(true);secondary.setBoolean(frame,true);
  var applied=type.getDeclaredField("suppressionApplied");applied.setAccessible(true);applied.setBoolean(frame,true);
  var readyField=type.getDeclaredField("ready");readyField.setAccessible(true);MutableState<Boolean> ready=(MutableState<Boolean>)readyField.get(frame);ready.setValue(true);
  var pass=type.getDeclaredField("passPainter");pass.setAccessible(true);MutableState<Object> passState=(MutableState<Object>)pass.get(frame);passState.setValue(fresh);
  var restore=type.getDeclaredMethod("restoreNative");restore.setAccessible(true);restore.invoke(frame);
  check(!secondary.getBoolean(frame)&&!ready.getValue()&&!applied.getBoolean(frame),"return to primary clears custom activation");
  check(passState.getValue()==null,"primary drops custom draw-pass source");
  check(rootField.get(frame)==root&&HostCoverBridge.lastInvalidated==root,"native layer restored while stable node is retained");
  check(source.invoke(frame)==fresh,"stable native source remains available for next secondary entry");
  ready.setValue(true); // Simulate a late position callback from the removed secondary Canvas.
  var suppress=type.getDeclaredMethod("suppress");suppress.setAccessible(true);
  check(!(Boolean)suppress.invoke(frame),"late Canvas readiness cannot hide primary artwork");
  restore.invoke(frame);check(!ready.getValue(),"repeated native restoration is safe");
  // A smaller incoming cover must remain observable during AnimatedContent.
  Object incoming=new Object();HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(fresh,root,620*620),new HostCoverBridge.Image(incoming,root,120*120)));
  artwork.setValue(incoming);check(source.invoke(frame)==fresh,"hold committed source during ambiguous overlap");
  Thread.sleep(150);check(source.invoke(frame)==incoming,"smaller incoming source commits after settle without oscillation");
  var failed=type.getDeclaredField("failed");failed.setAccessible(true);failed.setBoolean(frame,true);rejected.setValue(incoming);passState.setValue(fresh);
  var observe=type.getDeclaredMethod("observe",String.class);observe.setAccessible(true);
  check((Boolean)observe.invoke(frame,"album-B/track.flac"),"song switch schedules source refresh");
  check(!failed.getBoolean(frame)&&rejected.getValue()==null&&passState.getValue()==null,"song switch clears permanent failure/rejection and old draw pass");
  Object loaded=new Object();HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(loaded,root,420*420)));
  check((Boolean)observe.invoke(frame,"album-B/track.flac"),"late native load detected without parent composition or Painter capture");
  check(source.invoke(frame)==loaded,"late load becomes active source");
  check(!(Boolean)observe.invoke(frame,"album-B/track.flac"),"unchanged source does not schedule continuous redraws");
  failed.setBoolean(frame,true);rejected.setValue(loaded);
  check((Boolean)observe.invoke(frame,"album-C/track.flac")&&source.invoke(frame)==null,"old native painter is quarantined on next song");
  Object placeholder=new Object();HostCoverBridge.loading.add(placeholder);artwork.setValue(placeholder);
  HostCoverBridge.nodes.put(root,List.of(new HostCoverBridge.Image(placeholder,root,620*620),new HostCoverBridge.Image(loaded,root,120*120)));
  check(source.invoke(frame)==null,"placeholder and quarantined old cover cannot start new-song CD");
  System.out.println("PASS track transition recovery: incoming small source, per-song failure reset, late native load wake-up, reused cached source, placeholder exclusion and no redundant refresh");
  System.out.println("PASS secondary-to-primary handoff: native layer invalidation, stable root retained, pass source cleared, readiness reset, late callback cannot reactivate suppression");
  System.out.println("PASS production Frame.source: 100 simulated source changes, stale/closed source rejection, fresh-track recovery and detached-root cleanup");
  System.out.println("Live-node catalog is a test double; not a real SPW track-switch test.");
 }
}
