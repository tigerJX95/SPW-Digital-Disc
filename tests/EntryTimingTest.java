package io.github.tigerjx95.spwcdprobe;

import static io.github.tigerjx95.spwcdprobe.DiscTransition.Phase.*;

final class EntryTimingTest {
 public static void main(String[] args){
  var timing=new EntryTiming(ms(100));
  timing.clock(ms(102));
  timing.handoff(ms(7));
  timing.handoff(ms(2));
  timing.clock(ms(142));
  timing.paint(WAITING,ms(150),ms(16),ms(4));
  timing.clock(ms(156));
  timing.paint(WAITING,ms(180),ms(3),ms(1));
  timing.clock(ms(186));
  timing.drawRequested();
  timing.paint(COVER,ms(196),ms(2),ms(1));
  timing.clock(ms(202));
  timing.drawRequested();
  timing.drawRequested();
  timing.paint(OPENING,ms(237),ms(3),ms(2));
  timing.clock(ms(243));
  timing.paint(OPEN,ms(253),ms(1),ms(1));
  String line=timing.summary(2);
  for(String expected:new String[]{
      "entry=2 draws=5 firstPaintAfterUs=50000",
      "maxPaintGapUs=41000 gapPhase=OPENING gapAtUs=137000 paintGaps25ms=1",
      "maxClockGapUs=41000 clockGapAtUs=143000 clockGaps25ms=2",
      "maxHandoffUs=7000 maxCanvasUs=16000 maxReflectionUs=4000",
      "drawRequests=3 coverGapUs=16000 openingGapUs=41000 openGapUs=16000 handoffPasses=2"
  }){
   if(!line.contains(expected))throw new AssertionError(line);
  }
  System.out.println("PASS: entry timing reports the largest draw and clock gaps independently");
 }

 private static long ms(long value){return value*1_000_000L;}
}
