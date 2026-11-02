package io.github.tigerjx95.spwcdprobe;
import java.util.Objects;
/** Monotonic rotation with a time-based start. Hidden/paused time is not replayed. */
final class DiscRotation {
 static final long PERIOD=20_000_000_000L;
 static final long STARTUP=240_000_000L;
 private String song;
 private long last,runStarted;
 private boolean initialized,advancing;
 private double angle;
 float sample(String current,boolean enabled,boolean playing,boolean visible,long now){
  if(!initialized||!Objects.equals(song,current)){song=current;angle=0;advancing=false;initialized=true;last=now;}
  if(!enabled){angle=0;advancing=false;last=now;return 0;}
  now=Math.max(last,now);
  advance(now);
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
  // the angle. After startup, the original 20-second period is unchanged.
  double elapsed=now-last;
  elapsed-=startupDeficit(now-runStarted)-startupDeficit(last-runStarted);
  angle=(angle+(elapsed%PERIOD)*360.0/PERIOD)%360;
 }
 private static double startupDeficit(long elapsed){
  if(elapsed>=STARTUP)return STARTUP*.5;
  double u=Math.max(0,(double)elapsed/STARTUP);
  return STARTUP*(u-u*u*u+.5*u*u*u*u);
 }
 void reset(){initialized=false;advancing=false;angle=0;}
}
