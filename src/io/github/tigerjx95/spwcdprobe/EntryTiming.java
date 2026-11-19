package io.github.tigerjx95.spwcdprobe;

/** Bounded UI-thread timings for comparing two page entries in the same session. */
final class EntryTiming {
 private static final long GAP_THRESHOLD_NS=25_000_000L;
 private final long shown;
 private long firstPaint,previousPaint,previousClock;
 private long maxPaintGap,maxClockGap,maxHandoff,maxPaint,maxReflection;
 private long maxPaintGapAt,maxClockGapAt;
 private long maxCoverGap,maxOpeningGap,openGap;
 private int draws,longPaintGaps,longClockGaps,drawRequests,handoffPasses;
 private DiscTransition.Phase maxPaintGapPhase;

 EntryTiming(long shown){this.shown=shown;}

 void handoff(long duration){handoffPasses++;maxHandoff=Math.max(maxHandoff,duration);}

 void drawRequested(){drawRequests++;}

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
      +" openingGapUs="+micros(maxOpeningGap)+" openGapUs="+micros(openGap)+" handoffPasses="+handoffPasses;
 }

 private static long micros(long nanos){return nanos/1_000;}
}
