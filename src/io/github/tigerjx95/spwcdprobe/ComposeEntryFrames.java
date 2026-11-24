package io.github.tigerjx95.spwcdprobe;

import androidx.compose.runtime.MonotonicFrameClock;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.ContinuationImpl;

/** A Java suspend loop scoped to the presentation's Compose effect. */
final class ComposeEntryFrames extends ContinuationImpl {
 private final MonotonicFrameClock clock;
 private final BooleanSupplier frame;
 private final Runnable finished;
 private final Consumer<Throwable> failed;

 @SuppressWarnings("unchecked")
 private ComposeEntryFrames(Continuation<? super Unit> completion,MonotonicFrameClock clock,
     BooleanSupplier frame,Runnable finished,Consumer<Throwable> failed){
  super((Continuation<Object>)(Continuation<?>)completion);
  this.clock=clock;this.frame=frame;this.finished=finished;this.failed=failed;
 }

 static Object run(Continuation<? super Unit> completion,BooleanSupplier frame,Runnable finished,
     Consumer<Throwable> failed){
  var clock=completion.getContext().get(MonotonicFrameClock.Key);
  if(clock==null){finished.run();return Unit.INSTANCE;}
  return new ComposeEntryFrames(completion,clock,frame,finished,failed).invokeSuspend(Boolean.TRUE);
 }

 @Override protected Object invokeSuspend(Object result){
  try{
   for(;;){
    ResultKt.throwOnFailure(result);
    if(!Boolean.TRUE.equals(result))break;
    result=clock.withFrameNanos(ignored->frame.getAsBoolean(),this);
    if(result==IntrinsicsKt.getCOROUTINE_SUSPENDED())return result;
   }
  }catch(CancellationException cancelled){
   finished.run();
   throw cancelled;
  }catch(RuntimeException|LinkageError unavailable){
   failed.accept(unavailable);
  }
  finished.run();
  return Unit.INSTANCE;
 }
}
