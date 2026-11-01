package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javax.swing.SwingUtilities;
/** Uses the host's persisted settings; only UI-state publication runs on the EDT. */
final class DiscSettings implements AutoCloseable {
 static final String FRONT_KEY="disc_in_front";
 static final String ROTATE_KEY="disc_rotation";
 record Values(int amount,boolean front,boolean rotate){}
 static final Values DEFAULT=new Values(DiscExposure.DEFAULT,false,false);
 private final ConfigManager manager;
 private final Consumer<Values> update;
 private final AtomicLong revision=new AtomicLong();
 private final Consumer<ConfigHelper> listener=this::changed;
 private volatile boolean active=true;
 DiscSettings(ConfigManager manager,Consumer<Values> update){
  this.manager=manager;this.update=update;
  try{
   manager.addConfigChangeListener("config.json",listener);
   update.accept(read(manager.getConfig("config.json")));
  }catch(RuntimeException error){close();throw error;}
 }
 private static Values read(ConfigHelper config){
  int amount;boolean front;
  try{amount=DiscExposure.percent(config.get(DiscExposure.KEY,50f));}
  catch(RuntimeException invalid){amount=DiscExposure.DEFAULT;}
  try{Object value=config.get(FRONT_KEY,false);front=Boolean.TRUE.equals(value)||value instanceof String s&&s.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){front=false;}
  boolean rotate;
  try{Object value=config.get(ROTATE_KEY,false);rotate=Boolean.TRUE.equals(value)||value instanceof String text&&text.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){rotate=false;}
  return new Values(amount,front,rotate);
 }
 private void changed(ConfigHelper config){
  if(!active)return;
  long ticket=revision.incrementAndGet();
  Values value;
  try{value=read(config);}catch(RuntimeException invalid){value=DEFAULT;}
  final Values next=value;
  SwingUtilities.invokeLater(()->{if(active&&ticket==revision.get())update.accept(next);});
 }
 @Override public void close(){
  if(!active)return;active=false;revision.incrementAndGet();
  try{manager.removeConfigChangeListener(listener);}
  finally{if(manager instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}}
 }
}
