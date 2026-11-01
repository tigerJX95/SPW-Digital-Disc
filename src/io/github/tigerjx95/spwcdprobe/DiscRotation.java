package io.github.tigerjx95.spwcdprobe;
import java.util.Objects;
/** Monotonic, per-presentation clock. No elapsed hidden/paused time is replayed. */
final class DiscRotation {
 static final long PERIOD=20_000_000_000L;
 private String song;
 private long last;
 private boolean initialized,advancing;
 private double angle;
 float sample(String current,boolean enabled,boolean playing,boolean visible,long now){
  if(!initialized||!Objects.equals(song,current)){song=current;angle=0;advancing=false;initialized=true;last=now;}
  if(!enabled){angle=0;advancing=false;last=now;return 0;}
  if(advancing&&now>last)angle=(angle+((now-last)%PERIOD)*360.0/PERIOD)%360;
  last=now;advancing=playing&&visible&&current!=null;
  return (float)angle;
 }
 void reset(){initialized=false;advancing=false;angle=0;}
}
