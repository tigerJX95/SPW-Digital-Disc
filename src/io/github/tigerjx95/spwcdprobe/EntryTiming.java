package io.github.tigerjx95.spwcdprobe;

/** Bounded UI-thread timings for the first two entries after a style change. */
final class EntryTiming {
 static final class Series {
  private int generation,completed;
  int restart(){completed=0;return ++generation;}
  EntryTiming begin(long now){return completed<2?new EntryTiming(now,generation):null;}
  String complete(EntryTiming timing){
   return timing.generation==generation&&completed<2?timing.summary(++completed):null;
  }
 }
 private static final long GAP_THRESHOLD_NS=25_000_000L;
 private final long shown;
 private final int generation;
 private long firstPaint,previousPaint,previousClock;
 private long maxPaintGap,maxClockGap,maxHandoff,maxPaint,maxReflection;
 private long maxPaintGapAt,maxClockGapAt;
 private long maxCoverGap,maxOpeningGap,openGap,discReflectionAt=-1;
 private long previousHostFrame,maxHostFrameGap;
 private int hostFrames;
 private int draws,longPaintGaps,longClockGaps,drawRequests,handoffPasses;
 private DiscTransition.Phase maxPaintGapPhase;

 EntryTiming(long shown,int generation){this.shown=shown;this.generation=generation;}

 void handoff(long duration){handoffPasses++;maxHandoff=Math.max(maxHandoff,duration);}

 void drawRequested(){drawRequests++;}

 void discReflection(long now){
  if(discReflectionAt<0)discReflectionAt=now-shown;
 }

 void hostFrame(long now){
  if(hostFrames++>0)maxHostFrameGap=Math.max(maxHostFrameGap,now-previousHostFrame);
  previousHostFrame=now;
 }

 void clock(long now){
  if(draws!=0&&previousClock!=0){
   long gap=now-previousClock;
   if(gap>maxClockGap){maxClockGap=gap;maxClockGapAt=now-shown;}
   if(gap>=GAP_THRESHOLD_NS)longClockGaps++;
  }
  previousClock=now;
 }

 void paint(DiscTransition.Phase phase,long now,long duration,long reflection){
  if(draws++==0)firstPaint=now;
  else if(phase!=DiscTransition.Phase.WAITING){
   long gap=now-previousPaint;
   switch(phase){
    case COVER -> maxCoverGap=Math.max(maxCoverGap,gap);
    case OPENING -> maxOpeningGap=Math.max(maxOpeningGap,gap);
    case OPEN -> openGap=gap;
    default -> {}
   }
   if(gap>maxPaintGap){maxPaintGap=gap;maxPaintGapAt=now-shown;maxPaintGapPhase=phase;}
   if(gap>=GAP_THRESHOLD_NS)longPaintGaps++;
  }
  previousPaint=now;
  maxPaint=Math.max(maxPaint,duration);
  maxReflection=Math.max(maxReflection,reflection);
 }

 String summary(int entry){
  return "ENTRY_TIMING entry="+entry+" draws="+draws+" firstPaintAfterUs="+micros(firstPaint-shown)
      +" maxPaintGapUs="+micros(maxPaintGap)+" gapPhase="+maxPaintGapPhase+" gapAtUs="+micros(maxPaintGapAt)
      +" paintGaps25ms="+longPaintGaps+" maxClockGapUs="+micros(maxClockGap)+" clockGapAtUs="+micros(maxClockGapAt)+" clockGaps25ms="+longClockGaps
      +" maxHandoffUs="+micros(maxHandoff)+" maxCanvasUs="+micros(maxPaint)+" maxReflectionUs="+micros(maxReflection)
      +" drawRequests="+drawRequests+" coverGapUs="+micros(maxCoverGap)
      +" openingGapUs="+micros(maxOpeningGap)+" openGapUs="+micros(openGap)+" handoffPasses="+handoffPasses+" series="+generation
      +" hostFrames="+hostFrames+" hostFrameGapUs="+micros(maxHostFrameGap)
      +" discMirrorAtUs="+(discReflectionAt<0?-1:micros(discReflectionAt));
 }

 private static long micros(long nanos){return nanos/1_000;}
}
