package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javax.swing.SwingUtilities;
/** Uses the host's persisted settings; only UI-state publication runs on the EDT. */
final class DiscSettings implements AutoCloseable {
 static final String FRONT_KEY="disc_in_front";
 static final String ROTATE_KEY="disc_rotation";
 static final String VINYL_KEY="disc_vinyl";
 static final String MOTION_SPEED_KEY="disc_motion_speed";
 static final String ROTATION_SPEED_KEY="disc_rotation_speed";
 enum Speed {
  SLOW(1.35,30_000_000_000L),STANDARD(1.0,DiscRotation.PERIOD),FAST(.75,12_000_000_000L);
  private final double motionScale;
  final long rotationPeriod;
  Speed(double motionScale,long rotationPeriod){this.motionScale=motionScale;this.rotationPeriod=rotationPeriod;}
  long duration(long nanos){return Math.round(nanos*motionScale);}
 }
 record Values(int amount,boolean front,boolean rotate,boolean vinyl,Speed motionSpeed,Speed rotationSpeed){
  Values(int amount,boolean front,boolean rotate,boolean vinyl){this(amount,front,rotate,vinyl,Speed.STANDARD,Speed.STANDARD);}
 }
 static final Values DEFAULT=new Values(DiscExposure.DEFAULT,false,false,false);
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
  boolean vinyl;
  try{Object value=config.get(VINYL_KEY,false);vinyl=Boolean.TRUE.equals(value)||value instanceof String text&&text.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){vinyl=false;}
  return new Values(amount,front,rotate,vinyl,speed(config,MOTION_SPEED_KEY),speed(config,ROTATION_SPEED_KEY));
 }
 private static Speed speed(ConfigHelper config,String key){
  try{return Speed.valueOf(config.get(key,Speed.STANDARD.name()));}
  catch(RuntimeException invalid){return Speed.STANDARD;}
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
