package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import androidx.compose.runtime.MutableState;
import androidx.compose.ui.layout.LayoutCoordinates;
import com.xuncorp.spw.workshop.api.*;
/** Exercises the actual Frame, including the readiness reset done by content(). */
public class ProductionLayoutRecoveryTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.0.1",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");
  var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  var ready=(MutableState<Boolean>)field(type,"ready").get(frame);
  var motion=(DiscTransition)field(type,"motion").get(frame);
  var sync=type.getDeclaredMethod("syncVisibility",boolean.class);sync.setAccessible(true);
  var refresh=type.getDeclaredMethod("refreshReady");refresh.setAccessible(true);
  boolean[] attached={true};long[] measured={((long)900<<32)|680L};
  LayoutCoordinates coords=(LayoutCoordinates)Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->switch(m.getName()){
   case "isAttached" -> attached[0];case "getSize-YbymL2g" -> measured[0];
   default -> throw new UnsupportedOperationException(m.getName());
  });
  field(type,"widgetRoot").set(frame,coords);field(type,"widgetWidth").setFloat(frame,900);field(type,"widgetHeight").setFloat(frame,680);
  long now=1_000_000_000L;
  for(int i=0;i<100;i++){
   sync.invoke(frame,false);ready.setValue(false); // content() clears it while hidden.
   sync.invoke(frame,true);check(!ready.getValue(),"resume begins with cleared readiness");
   refresh.invoke(frame);check(ready.getValue(),"live Canvas recovers without any new position callback");
   motion.ready(now);motion.tick(now+940_000_000L);check(motion.pose().exposed()==1,"recovered scene finishes eject");
   now+=2_000_000_000L;
  }
  attached[0]=false;refresh.invoke(frame);check(!ready.getValue(),"detached Canvas cannot take over");
  attached[0]=true;refresh.invoke(frame);check(ready.getValue(),"reattached same Canvas recovers");
  measured[0]=((long)700<<32)|500L;refresh.invoke(frame);check(!ready.getValue(),"outdated size cannot take over");
  field(type,"widgetWidth").setFloat(frame,700);field(type,"widgetHeight").setFloat(frame,500);
  refresh.invoke(frame);check(ready.getValue(),"current layout size recovers");
  System.out.println("PASS actual Frame: 100 hidden/resume cycles with no position callback, detachment/reattachment and resize readiness recovery");
 }
}
