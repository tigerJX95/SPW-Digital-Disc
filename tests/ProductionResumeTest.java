package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.compose.runtime.MutableState;
import com.xuncorp.spw.workshop.api.*;
public class ProductionResumeTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","0.4.10.2",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  var sync=type.getDeclaredMethod("syncVisibility",boolean.class);sync.setAccessible(true);
  var pass=(MutableState<Object>)field(frame,"passPainter");var ready=(MutableState<Boolean>)field(frame,"ready");var pending=(MutableState<Boolean>)field(frame,"trackPending");
  var retained=(MutableState<Boolean>)field(frame,"retainingReturn");var motion=(DiscTransition)field(frame,"motion");var pose=(MutableState<DiscTransition.Pose>)field(frame,"pose");
  AtomicInteger navigation=new AtomicInteger();long now=1_000_000_000L;
  for(int i=0;i<100;i++){
   sync.invoke(frame,true);motion.ready(now);motion.tick(now+940_000_000L);pass.setValue(new Object());ready.setValue(true);pending.setValue(true);retained.setValue(true);
   motion.close(now+1_000_000_000L,navigation::incrementAndGet);
   sync.invoke(frame,false);check(pass.getValue()==null&&!pending.getValue()&&!retained.getValue(),"collapse clears old painter and pending requests");
   check(ready.getValue(),"reused layout readiness retained; no dependence on a fresh position callback");
   motion.tick(now+3_000_000_000L);check(navigation.get()==0,"hidden scene cannot execute stale deferred return");
   sync.invoke(frame,true);check(motion.phase()==DiscTransition.Phase.WAITING&&pose.getValue().exposed()==0,"production frame arms new entry with no stale disc");
   motion.ready(now+4_000_000_000L);motion.tick(now+4_590_000_000L);float half=motion.pose().exposed();
   sync.invoke(frame,true);check(motion.pose().exposed()==half,"same-visible update does not rewind");
   sync.invoke(frame,false);now+=6_000_000_000L;
  }
  System.out.println("PASS production Frame visibility lifecycle: 100 collapse/resume cycles clear stale painter/pending navigation, retain layout readiness, start new entry only once and cancel hidden callbacks");
 }
}
