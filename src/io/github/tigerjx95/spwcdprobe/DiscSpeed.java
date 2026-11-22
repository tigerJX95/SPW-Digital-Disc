package io.github.tigerjx95.spwcdprobe;

enum DiscSpeed {
 SLOW(1.35,30_000_000_000L),STANDARD(1.0,DiscRotation.PERIOD),FAST(.75,12_000_000_000L);

 private final double motionScale;
 final long rotationPeriod;

 DiscSpeed(double motionScale,long rotationPeriod){
  this.motionScale=motionScale;
  this.rotationPeriod=rotationPeriod;
 }

 long duration(long nanos){return Math.round(nanos*motionScale);}
}
