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
  System.out.println("PASS subpixel bounds across five window widths, exact once navigation, invalid geometry fallback, interrupted brake, cancellation and actual Frame clock");
 }
}
