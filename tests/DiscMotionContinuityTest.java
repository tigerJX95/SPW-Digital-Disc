package io.github.tigerjx95.spwcdprobe;
import java.util.concurrent.atomic.AtomicInteger;
/** Checks physical continuity and lifecycle properties, independent of curve coefficients. */
public class DiscMotionContinuityTest {
 static final long T=1_000_000_000L;
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static DiscTransition entry(boolean page){var m=new DiscTransition();if(page){m.enter();m.ready(T);m.tick(T+240_000_000L);}else{m.trackChanged();m.trackReady(T);}return m;}
 static long start(boolean page){return T+(page?240_000_000L:0);}
 static float exposure(long elapsed){var m=entry(false);m.tick(T+elapsed);return m.pose().exposed();}
 public static void main(String[] args){
  // Quiet start/end: distance in the first/last 10 ms is much lower than the
  // middle 10 ms. Each endpoint remains exactly bounded and unchanged in time.
  float begin=exposure(10_000_000),middle=exposure(355_000_000)-exposure(345_000_000),end=1-exposure(690_000_000);
  check(begin<middle/100&&end<middle/100,"quiet entry endpoints, not a duration slowdown");
  float prev=0;for(int us=0;us<=700_000;us+=100){float e=exposure(us*1000L);check(e>=prev&&e>=0&&e<=1,"dense bounded monotonic entry");prev=e;}
  check(exposure(700_000_000L)==1,"700 ms full eject retained");
  int sequences=0;
  for(boolean page:new boolean[]{false,true})for(int ms:new int[]{1,5,10,30,70,120,200,350,500,600,660,690,699}){
   long s=start(page),at=s+ms*1_000_000L;var m=entry(page);m.tick(at-100_000L);float before=m.pose().exposed();m.tick(at);var pose=m.pose();
   float incoming=(pose.exposed()-before)/.0001f;var calls=new AtomicInteger();m.close(at,calls::incrementAndGet);
   check(m.pose().equals(pose),"request retains exact pose");
   m.close(at,()->{throw new AssertionError("repeated close replaced completion");});
   m.tick(at+100_000L);float outgoing=(m.pose().exposed()-pose.exposed())/.0001f;
   check(Math.abs(outgoing-incoming)<.015f,"interrupted return preserves initial velocity: "+ms+"ms, "+incoming+" -> "+outgoing);
   float maximum=pose.exposed(),last=pose.exposed();boolean returning=false;
   for(int sample=1;sample<=750;sample++){
    m.tick(at+sample*1_000_000L);var p=m.pose();
    check(Float.isFinite(p.exposed())&&p.exposed()>=0&&p.exposed()<=1&&p.centered()>=0&&p.centered()<=1,"bounded pose without overshoot");
    maximum=Math.max(maximum,p.exposed());if(p.exposed()<last-1e-7f)returning=true;
    if(returning)check(p.exposed()<=last+1e-6f,"single turn, no repeated bounce");
    if(!returning&&page)check(Math.abs(p.centered()+p.exposed()-1)<1e-6f,"cover follows page-entry brake without a jump");
    if(sample>=80)check(p.exposed()<=last+1e-6f,"braking ends within 80 ms");
    if(calls.get()>0)check(p.exposed()==0&&m.finished(),"return callback runs only after hidden");last=p.exposed();
   }
   check(maximum>=pose.exposed()&&calls.get()==1&&m.finished(),"bounded one-shot completion within 730 ms");
   m.tick(at+10_000_000_000L);check(calls.get()==1,"late timer cannot repeat callback");sequences++;
  }
  // The final open state still returns in 650 ms, with a quiet endpoint.
  var m=entry(false);m.tick(T+700_000_000L);var calls=new AtomicInteger();m.close(T+800_000_000L,calls::incrementAndGet);
  m.tick(T+810_000_000L);check(1-m.pose().exposed()<middle/100,"quiet retract start");
  m.tick(T+1_440_000_000L);check(m.pose().exposed()<middle/100&&calls.get()==0,"quiet retract end before completion");
  m.tick(T+1_450_000_000L);check(calls.get()==1&&m.finished(),"650 ms normal retract retained");
  // Lifecycle cancellation must cancel the brake as well as the return.
  m=entry(false);m.tick(T+350_000_000L);m.close(T+350_000_000L,calls::incrementAndGet);int count=calls.get();m.reset();m.tick(T+5_000_000_000L);check(calls.get()==count,"reset cancels pending callback");
  m.trackChanged();m.trackReady(T);m.tick(T+700_000_000L);check(m.phase()==DiscTransition.Phase.OPEN&&m.pose().exposed()==1,"new track has no stale brake");
  System.out.println("PASS smooth motion: 7001 entry samples, "+sequences+" interrupted page/track sequences, pose/velocity continuity, bounded <=80ms braking, single reversal, exact 700/650ms endpoints and callback cancellation");
 }
}
