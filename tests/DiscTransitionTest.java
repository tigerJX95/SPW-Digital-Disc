package io.github.tigerjx95.spwcdprobe;
import java.util.concurrent.atomic.AtomicInteger;
public class DiscTransitionTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  DiscTransition m=new DiscTransition();long t=1_000_000_000L;AtomicInteger calls=new AtomicInteger();
  m.enter();m.tick(t+5_000_000_000L);check(m.phase()==DiscTransition.Phase.WAITING&&m.pose().exposed()==0,"loading cannot spend the entry clock");
  m.ready(t);m.tick(t+120_000_000);check(m.pose().exposed()==0&&m.pose().scale()>.94f&&m.pose().scale()<1,"cover enlarges first with zero disc");
  m.tick(t+240_000_000);check(m.pose().exposed()==0&&m.pose().scale()==1,"cover reaches full size before disc");
  float previous=0;
  for(int ms=0;ms<=700;ms+=10){m.tick(t+240_000_000L+ms*1_000_000L);float p=m.pose().exposed();check(p>=previous&&p<=1,"monotonic bounded eject");previous=p;}
  check(m.phase()==DiscTransition.Phase.OPEN&&previous==1,"exact settled endpoint");
  m.ready(t+1_000_000_000);check(m.pose().exposed()==1,"late image/track ready does not replay entry");
  long close=t+1_000_000_000L;m.close(close,calls::incrementAndGet);m.close(close,()->{throw new AssertionError("duplicate back");});
  previous=1;
  for(int ms=0;ms<650;ms+=10){m.tick(close+ms*1_000_000L);check(m.pose().exposed()<=previous,"monotonic retract");check(calls.get()==0,"host must wait until fully hidden");previous=m.pose().exposed();}
  m.tick(close+650_000_000);check(calls.get()==1&&m.pose().exposed()==0&&m.finished(),"hidden before single navigation callback");
  m.tick(close+10_000_000_000L);m.close(close,calls::incrementAndGet);check(calls.get()==1,"finished ignores repeated input");
  for(int ms:new int[]{0,60,240,300,560,939}){
   m.enter();m.ready(t);m.tick(t+ms*1_000_000L);float pos=m.pose().exposed();int n=calls.get();m.close(t+ms*1_000_000L,calls::incrementAndGet);
   check(m.pose().exposed()==pos,"reverse never jumps");m.tick(t+3_000_000_000L);check(calls.get()==n+1,"reverse completes once");
  }
  m.enter();m.ready(t);
  long midCover=t+120_000_000L;
  m.tick(midCover);float expanded=m.pose().scale();int beforeReturn=calls.get();
  m.close(midCover,calls::incrementAndGet);
  check(m.closing()&&m.pose().scale()==expanded,"cover return starts at current scale");
  m.tick(midCover+90_000_000L);
  check(m.pose().scale()<expanded&&m.pose().scale()>.94f&&m.pose().exposed()==0,"cover shrinks without exposing the disc");
  check(!m.finishSubpixelReturn(midCover+90_000_000L,500)&&calls.get()==beforeReturn,"disc tail optimization cannot skip cover return");
  m.tick(midCover+180_000_000L);
  check(m.finished()&&m.pose().scale()==.94f&&calls.get()==beforeReturn+1,"cover finishes before one return callback");
  m.enter();m.close(t,calls::incrementAndGet);check(m.finished(),"back while loading returns without waiting");
  m.enter();m.ready(t);m.tick(t+940_000_000);m.close(t+1_000_000_000L,calls::incrementAndGet);int n=calls.get();m.reset();m.tick(t+4_000_000_000L);check(calls.get()==n,"unload cancels delayed host callback");
  m.enter();m.ready(t);m.tick(t+100_000_000_000L);check(m.pose().exposed()==1,"delayed frame catches up using elapsed time");
  for(var speed:DiscSettings.Speed.values()){
   m.setSpeed(speed);m.enter();m.ready(t);
   long end=t+speed.duration(240_000_000L)+speed.duration(700_000_000L);
   m.tick(end-1_000_000L);check(m.phase()==DiscTransition.Phase.OPENING,"opening still active before preset endpoint");
   m.tick(end);check(m.phase()==DiscTransition.Phase.OPEN,"preset opening endpoint");
   int count=calls.get();m.close(end,calls::incrementAndGet);
   long closed=end+speed.duration(650_000_000L);
   m.tick(closed-1_000_000L);check(calls.get()==count,"no early preset completion");
   m.tick(closed);check(calls.get()==count+1,"preset close completes once");
   m.enter();m.ready(t);
   long mid=t+speed.duration(240_000_000L)+speed.duration(350_000_000L);
   m.tick(mid);var before=m.pose();m.close(mid,calls::incrementAndGet);
   check(m.pose().equals(before),"interruption retains pose at each speed");
   m.tick(mid+1_000_000L);check(m.pose().exposed()>before.exposed(),"interrupted motion brakes smoothly");
   m.tick(mid+2_000_000_000L);check(calls.get()==count+2,"interrupted return completes once");
  }
  System.out.println("PASS cover-first sequencing, 700ms progressive easing, monotonic retract, deferred/exactly-once return, repeated-click guard, reverse at six entry points, load wait, reset cancellation, delayed frame catchup");
 }
}
