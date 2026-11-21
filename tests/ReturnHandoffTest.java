package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.xuncorp.spw.workshop.api.*;
import androidx.compose.runtime.MutableState;
/** Endpoint pixel bound, exact callback ownership and production clock integration. */
public class ReturnHandoffTest {
 static final long T=1_000_000_000L;
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static DiscTransition opened(){var m=new DiscTransition();m.enter();m.ready(T);m.tick(T+940_000_000L);return m;}
 static Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
 public static void main(String[] args)throws Exception{
  for(float width:new float[]{180,500,900,1800,4000}){
   var m=opened();var calls=new AtomicInteger();long start=T+1_000_000_000L;m.close(start,calls::incrementAndGet);
   long completed=0;
   for(int ms=0;ms<=650;ms++){
    long now=start+ms*1_000_000L;m.tick(now);var p=m.pose();
    if(m.finishSubpixelReturn(now,width)){
     check(Math.max(p.exposed(),1-p.centered())*width<=.75f,"all remaining travel below physical pixel bound");
     check(m.pose().exposed()==0&&m.pose().centered()==1&&calls.get()==1,"exact hidden pose before one callback");completed=ms;break;
    }
    check(calls.get()==0,"no premature navigation");
   }
   check(completed>500&&completed<650,"measured tail removed without changing main retract");
   m.tick(start+5_000_000_000L);m.finishSubpixelReturn(start+5_000_000_000L,width);m.close(start,calls::incrementAndGet);
   check(calls.get()==1,"no repeated callback after endpoint");
   System.out.println("Return width "+width+" px: handoff at "+completed+" ms; nominal 650 ms");
  }
  for(float width:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){
   var m=opened();var n=new AtomicInteger();m.close(T+1_000_000_000L,n::incrementAndGet);m.tick(T+1_640_000_000L);
   check(!m.finishSubpixelReturn(T+1_640_000_000L,width)&&n.get()==0,"unknown geometry retains normal timing");
   m.tick(T+1_650_000_000L);check(n.get()==1,"normal fallback completes");
  }
  // During interrupted ejection even a tiny image cannot bypass braking.
  var m=new DiscTransition();m.trackChanged();m.trackReady(T);m.tick(T+350_000_000L);var n=new AtomicInteger();
  m.close(T+350_000_000L,n::incrementAndGet);m.tick(T+351_000_000L);
  check(!m.finishSubpixelReturn(T+351_000_000L,.1f)&&n.get()==0,"outgoing velocity brake preserved");
  m.reset();m.tick(T+5_000_000_000L);check(!m.finishSubpixelReturn(T+5_000_000_000L,500)&&n.get()==0,"hide/reset cancels completion");
  // Exercise the actual timer helper rather than only the transition class.
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.3.0",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  var motion=(DiscTransition)field(type,"motion").get(frame);field(type,"widgetWidth").setFloat(frame,900);
  var advance=type.getDeclaredMethod("advanceMotion",long.class);advance.setAccessible(true);
  motion.enter();motion.ready(T);motion.tick(T+940_000_000L);n.set(0);motion.close(T+1_000_000_000L,n::incrementAndGet);
  advance.invoke(frame,T+1_600_000_000L);check(n.get()==0,"production clock waits for visible tail");
  advance.invoke(frame,T+1_645_000_000L);check(n.get()==1&&motion.finished(),"production clock performs early pixel-bounded handoff");
  var pose=(MutableState<?>)field(type,"pose").get(frame);check(pose.getValue().equals(motion.pose()),"published pose agrees with completion");
  checkCoverReturn(type,frame,motion,pose,advance);
  System.out.println("PASS subpixel bounds across five window widths, exact once navigation, invalid geometry fallback, interrupted brake, cancellation and actual Frame clock");
 }

 static void checkCoverReturn(Class<?> type,Object frame,DiscTransition motion,
     MutableState<?> pose,Method advance)throws Exception{
  var originField=field(type,"coverOrigin");
  var originCtor=originField.getType().getDeclaredConstructors()[0];originCtor.setAccessible(true);
  var viewport=((MutableState<?>)field(type,"viewport").get(frame)).getValue();
  var bounds=new androidx.compose.ui.geometry.Rect(10,20,310,320);
  originField.set(frame,originCtor.newInstance(viewport,bounds));
  field(type,"coverHandoff").set(frame,CoverHandoff.from(bounds,500,1));
  field(type,"suppressionApplied").setBoolean(frame,true);
  var close=type.getDeclaredMethod("closeMotion",long.class,Runnable.class,boolean.class);close.setAccessible(true);
  long start=T+1_000_000_000L;
  for(boolean disc:new boolean[]{true,false}){
   motion.enter();motion.ready(T);motion.tick(T+940_000_000L);
   var calls=new AtomicInteger();
   Runnable returnToPlayer=()->{
    check(pose.getValue().equals(motion.pose()),"host callback receives the final pose");
    check(motion.pose().scale()==DiscTransition.COVER_START_SCALE,"cover is at native size before handoff");
    calls.incrementAndGet();
   };
   close.invoke(frame,start,returnToPlayer,disc);
   long travel=disc?420_000_000L:0L,slide=disc?230_000_000L:180_000_000L;
   if(disc){
    advance.invoke(frame,start+travel);
    check(motion.pose().exposed()>0&&motion.pose().scale()==1,"cover waits until the disc is almost hidden");
    advance.invoke(frame,start+travel+40_000_000L);
    check(motion.pose().exposed()>0&&motion.pose().scale()<1,"cover starts moving before the disc tail ends");
    float movingScale=motion.pose().scale();
    advance.invoke(frame,start+490_000_000L);
    check(motion.pose().exposed()==0&&motion.pose().scale()<movingScale,"cover keeps moving across the end of retraction");
   }
   else{
    advance.invoke(frame,start);
    check(motion.pose().exposed()==0&&motion.pose().scale()==1,"cover-only return starts from the current pose");
   }
   advance.invoke(frame,start+travel+slide/2);
   check(Math.abs(motion.pose().scale()-.97f)<.001f&&calls.get()==0,"cover slide cannot skip to the native view");
   advance.invoke(frame,start+travel+slide);
   advance.invoke(frame,start+2_000_000_000L);
   check(motion.finished()&&calls.get()==1,"both return modes finish within their existing time budget");
  }
  var waiting=new DiscTransition();
  waiting.trackChanged();
  waiting.close(T,()->{},true,true);
  check(waiting.pose().centered()==0,"return from track wait keeps its current center on the first frame");
  waiting.tick(T+90_000_000L);
  check(Math.abs(waiting.pose().centered()-.5f)<.001f,"track-wait return recenters smoothly");
  System.out.println("PASS overlapping disc and cover within 650 ms, cover-only return, track-wait continuity and final pose before host takeover");
 }
}
