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
  m.enter();m.close(t,calls::incrementAndGet);check(m.finished(),"back while loading returns without waiting");
  m.enter();m.ready(t);m.tick(t+940_000_000);m.close(t+1_000_000_000L,calls::incrementAndGet);int n=calls.get();m.reset();m.tick(t+4_000_000_000L);check(calls.get()==n,"unload cancels delayed host callback");
  m.enter();m.ready(t);m.tick(t+100_000_000_000L);check(m.pose().exposed()==1,"delayed frame catches up using elapsed time");
  System.out.println("PASS cover-first sequencing, 700ms progressive easing, monotonic retract, deferred/exactly-once return, repeated-click guard, reverse at six entry points, load wait, reset cancellation, delayed frame catchup");
 }
}
