package io.github.tigerjx95.spwcdprobe;

import androidx.compose.runtime.MutableState;
import com.xuncorp.spw.workshop.api.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the production Frame boundary methods, without native audio/Skia. */
public class ProductionRotationHandoffTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.2.0-test2",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");
  var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
  var observe=type.getDeclaredMethod("observe",String.class);observe.setAccessible(true);
  var close=type.getDeclaredMethod("closeMotion",long.class,Runnable.class,boolean.class);close.setAccessible(true);
  var sync=type.getDeclaredMethod("syncVisibility",boolean.class);sync.setAccessible(true);
  var restore=type.getDeclaredMethod("restoreNative");restore.setAccessible(true);
  for(boolean exposed:new boolean[]{false,true})for(int cycle=0;cycle<50;cycle++){
   Object frame=constructor.newInstance(plugin);
   var rotation=(DiscRotation)field(frame,"rotation");
   var angle=(MutableState<Float>)field(frame,"rotationAngle");
   var motion=(DiscTransition)field(frame,"motion");
   var pose=(MutableState<DiscTransition.Pose>)field(frame,"pose");
   observe.invoke(frame,"a");
   long t=1_000_000_000L;motion.trackChanged();motion.trackReady(t);motion.tick(t+700_000_000L);
   rotation.sample("a",true,true,true,t+700_000_000L);
   angle.setValue(rotation.sample("a",true,true,true,t+1_700_000_000L));
   check(angle.getValue()>0,"rotating before return");
   var completed=new AtomicInteger();
   close.invoke(frame,t+2_000_000_000L,(Runnable)completed::incrementAndGet,exposed);
   float frozen=angle.getValue();
   check(frozen>18,"return boundary samples elapsed angle once");
   for(int ms=1;ms<=700;ms++){
    motion.tick(t+2_000_000_000L+ms*1_000_000L);
    check(rotation.sample("a",true,true,false,t+2_000_000_000L+ms*1_000_000L)==frozen,"no extra timer rotation during retraction");
   }
   check(completed.get()==1&&motion.finished(),"single return after motion completion");
   check(pose.getValue().exposed()==(exposed?1:0),"return boundary publishes pose immediately");
   // Source observation may run before the next animation timer or draw.
   observe.invoke(frame,"b");check(angle.getValue()==0,"new source clears cached angle synchronously");
   check(rotation.sample("b",true,true,false,t+4_000_000_000L)==0,"new disc waits upright");
   sync.invoke(frame,true);
   rotation.sample("b",true,true,true,t+4_000_000_000L);
   angle.setValue(rotation.sample("b",true,true,true,t+5_000_000_000L));
   sync.invoke(frame,false);float hidden=angle.getValue();
   check(rotation.sample("b",true,true,false,Long.MAX_VALUE/2)==hidden,"hide freezes before timer can include hidden time");
   restore.invoke(frame);check(angle.getValue()==0,"primary page resets cached angle");
  }
  System.out.println("PASS production rotation boundaries: 100 Frame returns including zero exposure, immediate source reset, hidden freeze, primary reset and one-shot navigation");
 }
}
