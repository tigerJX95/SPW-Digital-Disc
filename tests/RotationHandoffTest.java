package io.github.tigerjx95.spwcdprobe;

/** Real rotation clock: timing invariance, quiet starts and no hidden catch-up. */
public class RotationHandoffTest {
 static final long T=1_000_000_000L;
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void near(float actual,float expected,String why){if(Math.abs(actual-expected)>.001f)throw new AssertionError(why+": "+actual+" != "+expected);}
 static float run(long step,long end){
  var r=new DiscRotation();r.sample("a",true,true,true,T);
  for(long at=step;at<end;at+=step)r.sample("a",true,true,true,T+at);
  return r.sample("a",true,true,true,T+end);
 }
 public static void main(String[] args){
  var r=new DiscRotation();r.sample("a",true,true,true,T);
  float previous=0,previousStep=0;
  for(int ms=1;ms<=240;ms++){
   float angle=r.sample("a",true,true,true,T+ms*1_000_000L),step=angle-previous;
   check(step>=previousStep-1e-6f&&step<=.01801f,"ramp accelerates monotonically without overspeed");
   if(ms==1)check(step<.00001f,"quiet first millisecond");
   previous=angle;previousStep=step;
  }
  near(r.sample("a",true,true,true,T+1_240_000_000L)-previous,18,"original steady speed after startup");
  for(long end:new long[]{120_000_000L,240_000_000L,5_000_000_000L,20_240_000_000L,120_000_000_000L}){
   float sparse=run(end,end);
   for(long step:new long[]{1_000_000L,7_000_000L,16_666_667L,33_333_333L,100_000_000L})near(run(step,end),sparse,"dropped-frame independent angle");
  }
  // Same-angle pause/resume, including a stop part way through acceleration.
  for(int stopMs:new int[]{0,1,30,120,239,240,800}){
   r=new DiscRotation();r.sample("a",true,true,true,T);
   long stop=T+stopMs*1_000_000L;
   float angle=r.sample("a",true,false,true,stop);
   near(r.sample("a",true,false,true,stop+50_000_000_000L),angle,"pause freezes");
   long resumed=stop+100_000_000_000L;
   near(r.sample("a",true,true,true,resumed),angle,"resume has no angle jump");
   check(r.sample("a",true,true,true,resumed+1_000_000L)-angle<.00002f,"resume restarts gently");
   float frozen=r.freeze(resumed+120_000_000L);
   for(int i=0;i<100;i++)near(r.sample("a",true,true,false,resumed+(i+1)*1_000_000_000L),frozen,"closing/hidden gate cannot rotate");
   near(r.sample("b",true,true,false,resumed+200_000_000_000L),0,"new track upright before ejection");
   near(r.sample("b",true,true,true,resumed+201_000_000_000L),0,"first open frame upright");
   near(r.sample("b",false,true,true,resumed+202_000_000_000L),0,"setting off resets");
  }
  System.out.println("PASS rotation handoff: 240 startup steps, monotonic bounded speed, frame-rate independence across wraps, partial-ramp pause/resume, immediate freeze and upright new-track entry");
 }
}
