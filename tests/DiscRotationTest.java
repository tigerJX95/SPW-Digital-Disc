package io.github.tigerjx95.spwcdprobe;
public class DiscRotationTest {
 static void near(float actual,float expected,String why){if(Math.abs(actual-expected)>.001f)throw new AssertionError(why+": "+actual);}
 public static void main(String[] args){
  var r=new DiscRotation();long t=1_000_000_000L;
  near(r.sample("a",true,true,true,t),0,"initial angle");
  near(r.sample("a",true,true,true,t+5_000_000_000L),87.84f,"20 second clockwise revolution after soft start");
  near(r.sample("a",true,false,true,t+10_000_000_000L),177.84f,"pause at current angle");
  near(r.sample("a",true,false,true,t+70_000_000_000L),177.84f,"paused wall time excluded");
  near(r.sample("a",true,true,true,t+75_000_000_000L),177.84f,"resume no jump");
  near(r.sample("a",true,true,false,t+80_000_000_000L),265.68f,"freeze during hidden/retract/not-ready");
  near(r.sample("a",true,true,true,t+180_000_000_000L),265.68f,"hidden time excluded");
  near(r.sample("a",true,true,true,t+185_000_000_000L),353.52f,"independent resumed soft starts");
  near(r.sample("b",true,true,true,t+186_000_000_000L),0,"new track resets despite playing");
  near(r.sample("b",true,true,true,t+191_000_000_000L),87.84f,"new track advances");
  near(r.sample("b",false,true,true,t+192_000_000_000L),0,"disable restores upright");
  near(r.sample("b",true,true,true,t+200_000_000_000L),0,"re-enable starts upright");
  r.reset();near(r.sample(null,true,true,true,t),0,"no media");
  near(r.sample(null,true,true,true,t+5_000_000_000L),0,"no media never spins");
  near(r.sample("c",true,true,false,t+10_000_000_000L),0,"opening waits upright");
  near(r.sample("c",true,true,true,t+30_000_000_000L),0,"first ready frame upright");
  near(r.sample("c",true,true,true,t+35_000_000_000L),87.84f,"ready resumes");
  r.reset();
  long fast=DiscSettings.Speed.FAST.rotationPeriod,slow=DiscSettings.Speed.SLOW.rotationPeriod;
  r.sample("speed",true,true,true,t,fast);
  near(r.sample("speed",true,true,true,t+3_120_000_000L,slow),90,"speed change integrates old rate up to the change");
  near(r.sample("speed",true,true,true,t+10_620_000_000L,slow),180,"new rate continues from same angle");
  System.out.println("PASS rotation clock: speed, pause/resume, hidden/readiness freeze, modulo, track reset, toggle reset, lifecycle reset and null media");
 }
}
