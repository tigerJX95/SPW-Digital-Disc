package io.github.tigerjx95.spwcdprobe;

/** UI-thread, monotonic-time choreography. No file loading or host calls in draw. */
final class DiscTransition {
 enum Phase { IDLE, WAITING, TRACK_WAIT, COVER, OPENING, OPEN, CLOSING, FINISHED }
 record Pose(float exposed,float scale,float centered) {}
 private Phase phase=Phase.IDLE;
 private long started,duration;
 private float exposure,scale=1,closeFrom,centered,closeCentered;
 private boolean pageEntry;
 private Runnable completion;
 Phase phase(){return phase;}
 Pose pose(){return new Pose(exposure,scale,centered);}
 boolean closing(){return phase==Phase.CLOSING;}
 boolean finished(){return phase==Phase.FINISHED;}
 void enter(){reset();phase=Phase.WAITING;scale=.94f;centered=1;pageEntry=true;}
 void ready(long now){if(phase==Phase.WAITING){phase=Phase.COVER;started=now;}}
 void trackChanged(){
  if(closing()||finished())return;
  phase=Phase.TRACK_WAIT;exposure=0;scale=1;centered=0;pageEntry=false;completion=null;
 }
 void trackReady(long now){if(phase==Phase.TRACK_WAIT){phase=Phase.OPENING;started=now;}}
 void reset(){phase=Phase.IDLE;exposure=0;scale=1;centered=0;completion=null;}
 void close(long now,Runnable done){
  close(now,done,true);
 }
 void close(long now,Runnable done,boolean visibleDisc){
  if(phase==Phase.CLOSING||phase==Phase.FINISHED)return;
  tick(now);
  if(!visibleDisc||phase==Phase.IDLE||phase==Phase.WAITING||phase==Phase.TRACK_WAIT){phase=Phase.FINISHED;exposure=0;scale=1;done.run();return;}
  completion=done;closeFrom=exposure;closeCentered=centered;started=now;
  duration=(long)(650_000_000L*Math.sqrt(closeFrom));
  // With no exposed pixels there is no return motion to wait for.
  if(closeFrom==0){finish();return;}
  phase=Phase.CLOSING;
 }
 boolean tick(long now){
  float old=exposure,oldScale=scale,oldCentered=centered;Phase before=phase;
  if(phase==Phase.COVER){
   float t=unit(now-started,240_000_000L);scale=.94f+.06f*smoother(t);
   if(t>=1){phase=Phase.OPENING;started+=240_000_000L;}
  }
  if(phase==Phase.OPENING){
   // ReBORN's progressive entry: 700 ms smoothstep, quiet at both ends.
   float t=unit(now-started,700_000_000L);exposure=smooth(t);scale=1;centered=pageEntry?1-exposure:0;
   if(t>=1){phase=Phase.OPEN;exposure=1;}
  }else if(phase==Phase.CLOSING){
   float t=unit(now-started,duration);exposure=closeFrom*(1-smooth(t));centered=closeCentered+(1-closeCentered)*smooth(t);
   if(t>=1)finish();
  }
  return old!=exposure||oldScale!=scale||oldCentered!=centered||before!=phase;
 }
 private void finish(){
  exposure=0;phase=Phase.FINISHED;
  Runnable done=completion;completion=null; // Clear before re-entrant host callbacks.
  if(done!=null)done.run();
 }
 private static float unit(long elapsed,long duration){return duration<=0?1:Math.max(0,Math.min(1,(float)((double)elapsed/duration)));}
 private static float smooth(float t){return t*t*(3-2*t);}
 private static float smoother(float t){return t*t*t*(t*(t*6-15)+10);}
}
