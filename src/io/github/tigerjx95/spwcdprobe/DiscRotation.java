package io.github.tigerjx95.spwcdprobe;
import java.util.Objects;
/** Monotonic rotation with a time-based start. Hidden/paused time is not replayed. */
final class DiscRotation {
 static final long PERIOD=20_000_000_000L;
 static final long STARTUP=240_000_000L;
 private String song;
 private long last,runStarted,period=PERIOD;
 private boolean initialized,advancing;
 private double angle;
 float sample(String current,boolean enabled,boolean playing,boolean visible,long now){
  return sample(current,enabled,playing,visible,now,PERIOD);
 }
 float sample(String current,boolean enabled,boolean playing,boolean visible,long now,long nextPeriod){
  if(!initialized||!Objects.equals(song,current)){song=current;angle=0;advancing=false;initialized=true;last=now;}
  if(!enabled){angle=0;advancing=false;last=now;return 0;}
  now=Math.max(last,now);
  advance(now);
  period=nextPeriod;
  boolean next=playing&&visible&&current!=null;
  if(next&&!advancing)runStarted=now;
  last=now;advancing=next;
  return (float)angle;
 }
 float freeze(long now){
  now=Math.max(last,now);advance(now);last=now;advancing=false;return (float)angle;
 }
 private void advance(long now){
  if(!advancing||now<=last)return;
  // Integrate smoothstep angular speed, so dropped frames cannot change
  // the angle. Apply the prior period through the current sample.
  double elapsed=now-last;
  elapsed-=startupDeficit(now-runStarted)-startupDeficit(last-runStarted);
  angle=(angle+(elapsed%period)*360.0/period)%360;
 }
 private static double startupDeficit(long elapsed){
  if(elapsed>=STARTUP)return STARTUP*.5;
  double u=Math.max(0,(double)elapsed/STARTUP);
  return STARTUP*(u-u*u*u+.5*u*u*u*u);
 }
 void reset(){initialized=false;advancing=false;angle=0;}
}
