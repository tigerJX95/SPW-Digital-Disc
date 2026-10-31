package io.github.tigerjx95.spwcdprobe;
/** A new presentation begins only after the host sheet has fully settled. */
final class RevealCycle {
 enum Edge { NONE, HIDDEN, SHOWN }
 private boolean initialized,visible;
 Edge sample(boolean settledVisible){
  if(initialized&&visible==settledVisible)return Edge.NONE;
  initialized=true;visible=settledVisible;
  return visible?Edge.SHOWN:Edge.HIDDEN;
 }
 void reset(){initialized=false;visible=false;}
}
