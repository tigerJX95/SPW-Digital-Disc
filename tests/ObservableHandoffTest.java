package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.compose.runtime.MutableState;
import androidx.compose.runtime.snapshots.*;
import com.xuncorp.spw.workshop.api.*;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
public class ObservableHandoffTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var observer=new SnapshotStateObserver(callback->{callback.invoke();return Unit.INSTANCE;});observer.start();
  Object[] oldPlain={null};AtomicInteger oldRefresh=new AtomicInteger();Object oldScope=new Object();
  observer.observeReads(oldScope,s->{oldRefresh.incrementAndGet();return Unit.INSTANCE;},()->{if(oldPlain[0]==null)return Unit.INSTANCE;return Unit.INSTANCE;});
  oldPlain[0]=new Object();Snapshot.Companion.sendApplyNotifications();
  check(oldRefresh.get()==0,"old plain handoff never invalidates an empty cached draw");observer.clear(oldScope);
  var plugin=new CdPageProbePlugin(new PluginContext("test","0.4.9.8",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  var field=type.getDeclaredField("passPainter");field.setAccessible(true);MutableState<Object> source=(MutableState<Object>)field.get(frame);
  Snapshot.Companion.notifyObjectsInitialized();Snapshot.Companion.sendApplyNotifications();
  Object nativeScope=new Object(),canvasScope=new Object();AtomicInteger nativeRefresh=new AtomicInteger(),canvasRefresh=new AtomicInteger();
  Function1<Object,Unit> nativeChanged=s->{nativeRefresh.incrementAndGet();return Unit.INSTANCE;};
  Function1<Object,Unit> canvasChanged=s->{canvasRefresh.incrementAndGet();return Unit.INSTANCE;};
  Runnable recordDraws=()->{
   observer.observeReads(nativeScope,nativeChanged,()->{source.getValue();return Unit.INSTANCE;});
   observer.observeReads(canvasScope,canvasChanged,()->{Object p=source.getValue();if(p==null)return Unit.INSTANCE;return Unit.INSTANCE;});
  };
  recordDraws.run();source.setValue(new Object());Snapshot.Companion.sendApplyNotifications();
  check(nativeRefresh.get()==1&&canvasRefresh.get()==1,"initial null-to-ready wakes both native and custom cached draws");
  for(int i=0;i<100;i++){
   recordDraws.run();source.setValue(null);Snapshot.Companion.sendApplyNotifications();
   int n=nativeRefresh.get(),c=canvasRefresh.get();
   recordDraws.run();source.setValue(new Object());Snapshot.Companion.sendApplyNotifications();
   check(nativeRefresh.get()==n+1&&canvasRefresh.get()==c+1,"empty draw stays subscribed on track "+i);
  }
  observer.stop();observer.clear();
  System.out.println("PASS actual host SnapshotStateObserver reproduces old plain-field missed invalidation, then production Frame.passPainter wakes native and Canvas scopes after initial empty draw and 100 pending-to-ready track transitions");
  System.out.println("Framework invalidation regression; not a Windows SPW playback test.");
 }
}
