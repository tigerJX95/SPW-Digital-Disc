package io.github.tigerjx95.spwcdprobe;

/** UI-thread, monotonic-time choreography. No file loading or host calls in draw. */
final class DiscTransition {
 enum Phase { IDLE, WAITING, TRACK_WAIT, COVER, OPENING, OPEN, CLOSING, FINISHED }
 record Pose(float exposed,float scale,float centered) {}
 private Phase phase=Phase.IDLE;
 private long started,duration,brakeDuration;
 private float exposure,scale=1,closeFrom,centered,closeCentered;
 private float brakeFrom,brakeCentered;
 private double brakeVelocity;
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
 void reset(){phase=Phase.IDLE;exposure=0;scale=1;centered=0;completion=null;brakeDuration=0;brakeVelocity=0;}
 void close(long now,Runnable done){
  close(now,done,true);
 }
 void close(long now,Runnable done,boolean visibleDisc){
  if(phase==Phase.CLOSING||phase==Phase.FINISHED)return;
  tick(now);
  if(!visibleDisc||phase==Phase.IDLE||phase==Phase.WAITING||phase==Phase.TRACK_WAIT){phase=Phase.FINISHED;exposure=0;scale=1;done.run();return;}
  completion=done;
  // Preserve outgoing velocity on an interrupted eject. A short braking
  // segment stops inside the original endpoint before the return begins.
  brakeFrom=exposure;brakeCentered=centered;brakeDuration=0;brakeVelocity=0;
  if(phase==Phase.OPENING&&exposure>0&&exposure<1){
   double u=unit(now-started,700_000_000L);
   brakeVelocity=30*u*u*(1-u)*(1-u)/.7; // Exposure units per second.
   if(brakeVelocity>0){
    double seconds=Math.min(Math.min(.08,Math.max(0,now-started)/1_000_000_000.0),3*(1-exposure)/brakeVelocity);
    brakeDuration=(long)(seconds*1_000_000_000L);
   }
  }
  closeFrom=Math.min(1,brakeFrom+(float)(brakeVelocity*brakeDuration/3_000_000_000.0));
  closeCentered=pageEntry?Math.max(0,brakeCentered-(closeFrom-brakeFrom)):brakeCentered;
  started=now;duration=(long)(650_000_000L*Math.sqrt(closeFrom));
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
   // Quintic easing keeps velocity and acceleration quiet at both endpoints.
   float t=unit(now-started,700_000_000L);exposure=smoother(t);scale=1;centered=pageEntry?1-exposure:0;
   if(t>=1){phase=Phase.OPEN;exposure=1;}
  }else if(phase==Phase.CLOSING){
   long elapsed=now-started;
   if(elapsed<brakeDuration){
    double u=unit(elapsed,brakeDuration);
    exposure=Math.min(closeFrom,brakeFrom+(float)(brakeVelocity*brakeDuration/1_000_000_000.0*(u-u*u+u*u*u/3)));
    centered=pageEntry?Math.max(0,brakeCentered-(exposure-brakeFrom)):brakeCentered;
   }else{
    float t=unit(elapsed-brakeDuration,duration),eased=smoother(t);
    exposure=closeFrom*(1-eased);centered=closeCentered+(1-closeCentered)*eased;
    if(t>=1)finish();
   }
  }
  return old!=exposure||oldScale!=scale||oldCentered!=centered||before!=phase;
 }
 private void finish(){
  exposure=0;centered=1;phase=Phase.FINISHED;
  Runnable done=completion;completion=null; // Clear before re-entrant host callbacks.
  if(done!=null)done.run();
 }
 // The timer may stop waiting for the eased tail only when every remaining
 // displacement is below a physical pixel. Normal tick() timing stays exact;
 // callers without measured geometry retain the full animation duration.
 boolean finishSubpixelReturn(long now,float travelPixels){
  if(phase!=Phase.CLOSING||now-started<=brakeDuration||!Float.isFinite(travelPixels)||travelPixels<=0)return false;
  float remaining=Math.max(exposure,1-centered)*travelPixels;
  if(remaining>.75f)return false;
  finish();return true;
 }
 private static float unit(long elapsed,long duration){return duration<=0?1:Math.max(0,Math.min(1,(float)((double)elapsed/duration)));}
 private static float smoother(float t){double u=t;return (float)Math.max(0,Math.min(1,u*u*u*(u*(u*6-15)+10)));}
}
