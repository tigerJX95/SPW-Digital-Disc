package io.github.tigerjx95.spwcdprobe;

import androidx.compose.runtime.BroadcastFrameClock;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import static io.github.tigerjx95.spwcdprobe.DiscTransition.Phase.*;

final class EntryTimingTest {
 public static void main(String[] args)throws Exception{
  var series=new EntryTiming.Series();
  var timing=series.begin(ms(100));
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
  timing.hostFrame(ms(202));
  timing.hostFrame(ms(237));
  timing.drawRequested();
  timing.drawRequested();
  timing.discReflection(ms(237));
  timing.discReflection(ms(252));
  timing.paint(OPENING,ms(237),ms(3),ms(2));
  timing.clock(ms(243));
  timing.paint(OPEN,ms(253),ms(1),ms(1));
  String line=timing.summary(2);
  for(String expected:new String[]{
      "entry=2 draws=5 firstPaintAfterUs=50000",
      "maxPaintGapUs=41000 gapPhase=OPENING gapAtUs=137000 paintGaps25ms=1",
      "maxClockGapUs=41000 clockGapAtUs=143000 clockGaps25ms=2",
      "maxHandoffUs=7000 maxCanvasUs=16000 maxReflectionUs=4000",
      "drawRequests=3 coverGapUs=16000 openingGapUs=41000 openGapUs=16000 handoffPasses=2",
      "hostFrames=2 hostFrameGapUs=35000 discMirrorAtUs=137000"
  }){
   if(!line.contains(expected))throw new AssertionError(line);
  }
  if(!series.complete(timing).contains("entry=1"))throw new AssertionError("first completed entry");
  var old=series.begin(ms(300));
  int revision=series.restart();
  if(series.complete(old)!=null)throw new AssertionError("in-flight entry from the old style cannot consume the new sample");
  for(int i=1;i<=2;i++){
   var entry=series.begin(ms(300+i));
   entry.paint(OPEN,ms(400+i),ms(1),0);
   var next=series.complete(entry);
   if(!next.contains("entry="+i+" ")||!next.contains("series="+revision+" "))throw new AssertionError(next);
  }
  if(series.begin(ms(500))!=null)throw new AssertionError("two paired entries are sufficient");
  series.restart();
  if(series.begin(ms(600))==null)throw new AssertionError("a subsequent style change must rearm the timing");
  verifyComposeFrames();
  verifyTimerHandoff();
  System.out.println("PASS: entry timing reports the largest draw and clock gaps independently");
 }

 private static void verifyTimerHandoff()throws Exception{
  javax.swing.SwingUtilities.invokeAndWait(()->{
   try{
    var plugin=new CdPageProbePlugin(new com.xuncorp.spw.workshop.api.PluginContext(
        "test","1.12.0",".","1.19.3",com.xuncorp.spw.workshop.api.Channel.MS));
    field(plugin,"running").setBoolean(plugin,true);
    var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");
    var constructor=type.getDeclaredConstructors()[0];constructor.setAccessible(true);
    var frame=constructor.newInstance(plugin);
    var coords=androidx.compose.ui.layout.LayoutCoordinates.class;
    field(frame,"widgetRoot").set(frame,java.lang.reflect.Proxy.newProxyInstance(coords.getClassLoader(),
        new Class<?>[]{coords},(object,method,args)->true));
    field(frame,"suppressionApplied").setBoolean(frame,true);
    @SuppressWarnings("unchecked") var ready=(androidx.compose.runtime.MutableState<Boolean>)field(frame,"ready").get(frame);
    ready.setValue(true);
    var motion=(DiscTransition)field(frame,"motion").get(frame);
    var timer=type.getDeclaredMethod("advanceFromTimer",long.class);timer.setAccessible(true);
    var drive=type.getDeclaredMethod("driveEntryFrames",Continuation.class);drive.setAccessible(true);
    long now=System.nanoTime();
    motion.enter();motion.ready(now-ms(250));
    timer.invoke(frame,now);
    if(motion.phase()!=OPENING||motion.pose().exposed()==0)throw new AssertionError("timer starts without the first host callback");
    var clock=new BroadcastFrameClock();
    drive.invoke(frame,new Completion(clock));
    clock.sendFrame(System.nanoTime());
    if(field(frame,"entryClockOwner").get(frame)==null)throw new AssertionError("native frame owns animation");
    long last=field(frame,"lastEntryFrame").getLong(frame);
    var pose=motion.pose();
    timer.invoke(frame,last+ms(100));
    if(!motion.pose().equals(pose))throw new AssertionError("timer cannot advance a native-owned animation");
    timer.invoke(frame,last+ms(510));
    if(field(frame,"entryClockOwner").get(frame)!=null||motion.pose().exposed()<=pose.exposed())
     throw new AssertionError("a stalled host clock must yield to the timer");
    clock.sendFrame(System.nanoTime());
    if(clock.getHasAwaiters())throw new AssertionError("timed-out effect must stop");
   }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
  });
  System.out.println("PASS: source-ready timer start, exclusive Compose handoff and stalled-clock recovery");
 }

 private static java.lang.reflect.Field field(Object instance,String name)throws ReflectiveOperationException{
  var field=instance.getClass().getDeclaredField(name);
  field.setAccessible(true);
  return field;
 }

 private static void verifyComposeFrames(){
  var clock=new BroadcastFrameClock();
  var completion=new Completion(clock);
  var frames=new AtomicInteger();
  var finished=new AtomicInteger();
  Object result=ComposeEntryFrames.run(completion,()->frames.incrementAndGet()<3,finished::incrementAndGet,
      error->{throw new AssertionError(error);});
  if(result!=IntrinsicsKt.getCOROUTINE_SUSPENDED()||!clock.getHasAwaiters())throw new AssertionError("wait for a native frame");
  for(int frame=1;frame<=3;frame++){
   clock.sendFrame(ms(frame*16));
   if(frames.get()!=frame||clock.getHasAwaiters()!=(frame<3))throw new AssertionError("exactly one step per native frame");
  }
  if(finished.get()!=1||completion.result!=Unit.INSTANCE)throw new AssertionError("finish once after the last frame");

  var unavailable=new Completion(EmptyCoroutineContext.INSTANCE);
  result=ComposeEntryFrames.run(unavailable,()->{throw new AssertionError("missing clock must leave the timer in control");},
      ()->{},error->{throw new AssertionError(error);});
  if(result!=Unit.INSTANCE||unavailable.result!=null)throw new AssertionError("no suspension without a clock");

  var cancelledClock=new BroadcastFrameClock();
  var cancelled=new Completion(cancelledClock);
  ComposeEntryFrames.run(cancelled,()->{throw new AssertionError("cancelled view must not advance");},
      finished::incrementAndGet,error->{throw new AssertionError(error);});
  cancelledClock.cancel(new CancellationException("view disposed"));
  try{ResultKt.throwOnFailure(cancelled.result);throw new AssertionError("cancellation must reach the composition");}
  catch(CancellationException expected){if(finished.get()!=2)throw new AssertionError("cancellation releases the clock");}
  System.out.println("PASS: Compose frame callbacks are one-shot, rescheduled per frame and released on cancellation");
 }

 private static final class Completion implements Continuation<Unit>{
  private final CoroutineContext context;
  Object result;
  Completion(CoroutineContext context){this.context=context;}
  public CoroutineContext getContext(){return context;}
  public void resumeWith(Object result){this.result=result;}
 }

 private static long ms(long value){return value*1_000_000L;}
}
