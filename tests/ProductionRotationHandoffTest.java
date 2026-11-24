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
  var observe=type.getDeclaredMethod("observe",String.class,DiscSettings.AlbumKey.class);observe.setAccessible(true);
  var close=type.getDeclaredMethod("closeMotion",long.class,Runnable.class,boolean.class);close.setAccessible(true);
  var sync=type.getDeclaredMethod("syncVisibility",boolean.class);sync.setAccessible(true);
  var restore=type.getDeclaredMethod("restoreNative");restore.setAccessible(true);
  for(boolean exposed:new boolean[]{false,true})for(int cycle=0;cycle<50;cycle++){
   Object frame=constructor.newInstance(plugin);
   var rotation=(DiscRotation)field(frame,"rotation");
   var angle=(MutableState<Float>)field(frame,"rotationAngle");
   var motion=(DiscTransition)field(frame,"motion");
   var pose=(MutableState<DiscTransition.Pose>)field(frame,"pose");
   observe.invoke(frame,"a",null);
   long t=1_000_000_000L;motion.trackChanged();motion.trackReady(t);motion.tick(t+700_000_000L);
   rotation.sample("a",true,true,true,t+700_000_000L);
   angle.setValue(rotation.sample("a",true,true,true,t+1_700_000_000L));
   check(angle.getValue()>0,"rotating before return");
   var completed=new AtomicInteger();
   close.invoke(frame,t+2_000_000_000L,(Runnable)()->{
    check(pose.getValue().equals(motion.pose()),"host callback sees the completed return pose");
    completed.incrementAndGet();
   },exposed);
   check(pose.getValue().exposed()==(exposed?1:0),"return boundary publishes pose immediately");
   float frozen=angle.getValue();
   check(frozen>18,"return boundary samples elapsed angle once");
   for(int ms=1;ms<=700;ms++){
    motion.tick(t+2_000_000_000L+ms*1_000_000L);
    check(rotation.sample("a",true,true,false,t+2_000_000_000L+ms*1_000_000L)==frozen,"no extra timer rotation during retraction");
   }
   check(completed.get()==1&&motion.finished(),"single return after motion completion");
   // Source observation may run before the next animation timer or draw.
   observe.invoke(frame,"b",null);check(angle.getValue()==0,"new source clears cached angle synchronously");
   check(rotation.sample("b",true,true,false,t+4_000_000_000L)==0,"new disc waits upright");
   sync.invoke(frame,true);
   rotation.sample("b",true,true,true,t+4_000_000_000L);
   angle.setValue(rotation.sample("b",true,true,true,t+5_000_000_000L));
   sync.invoke(frame,false);float hidden=angle.getValue();
   check(rotation.sample("b",true,true,false,Long.MAX_VALUE/2)==hidden,"hide freezes before timer can include hidden time");
   restore.invoke(frame);check(angle.getValue()==0,"primary page resets cached angle");
  }
  Object hoverFrame=constructor.newInstance(plugin);
  var hoverMotion=(DiscTransition)field(hoverFrame,"motion");
  var hover=(CoverHover)field(hoverFrame,"coverHover");
  var hoverTilt=(MutableState<CoverHover.Tilt>)field(hoverFrame,"coverTilt");
  ((MutableState<Boolean>)field(hoverFrame,"ready")).setValue(true);
  ((MutableState<Object>)field(hoverFrame,"passPainter")).setValue(new Object());
  var pointer=type.getDeclaredMethod("moveCoverPointer",long.class);pointer.setAccessible(true);
  var sampleHover=type.getDeclaredMethod("sampleCoverHover",long.class);sampleHover.setAccessible(true);
  long t=2_000_000_000L;
  hoverMotion.trackChanged();hoverMotion.trackReady(t);
  hover.bounds(0,0,400,400);
  pointer.invoke(hoverFrame,RootRenderer.pack(360,55));sampleHover.invoke(hoverFrame,t);
  check(hoverTilt.getValue()==CoverHover.Tilt.FLAT,"ignore input during disc ejection");
  hoverMotion.tick(t+700_000_000L);hover.bounds(0,0,400,400);
  pointer.invoke(hoverFrame,RootRenderer.pack(360,55));
  long sampled=System.nanoTime()+16_000_000L;
  sampleHover.invoke(hoverFrame,sampled);
  check(hoverTilt.getValue().x()>0&&hoverTilt.getValue().y()<0,"open cover publishes hover tilt");
  var drawn=hoverTilt.getValue();
  ((RootRenderer.CoverLayer)field(hoverFrame,"coverLayer")).tilt=drawn;
  pointer.invoke(hoverFrame,RootRenderer.pack(30,340));sampleHover.invoke(hoverFrame,sampled+16_000_000L);
  check(!hoverTilt.getValue().equals(drawn),"a clock sample can be newer than the last painted frame");
  long closeAt=sampled+20_000_000L;
  close.invoke(hoverFrame,closeAt,(Runnable)()->{},true);
  check(hoverTilt.getValue()==drawn,"retraction starts from the painted angle");
  var control=new DiscTransition();
  control.trackChanged();control.trackReady(t);control.tick(t+700_000_000L);
  control.close(closeAt,()->{},true);
  var advance=type.getDeclaredMethod("advanceMotion",long.class);advance.setAccessible(true);
  var hoverPose=(MutableState<DiscTransition.Pose>)field(hoverFrame,"pose");
  pointer.invoke(hoverFrame,RootRenderer.pack(30,340));
  for(int ms=0;ms<=660;ms++){
   long now=closeAt+ms*1_000_000L;
   control.tick(now);advance.invoke(hoverFrame,now);
   check(hoverPose.getValue().equals(control.pose()),"hover shares the original retraction timing at "+ms+" ms");
   if(ms==60)check(Math.abs(hoverTilt.getValue().x()-drawn.x()*.5f)<.0001f
       &&hoverPose.getValue().exposed()<1,"sleeve returns halfway while the disc is already moving");
   if(ms>=120)check(hoverTilt.getValue()==CoverHover.Tilt.FLAT,"hover is flat before host handoff");
  }
  System.out.println("PASS production rotation boundaries: 100 Frame returns including zero exposure, immediate source reset, hidden freeze, primary reset and one-shot navigation");
 }
}
