package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.util.List;
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
 static final String LOCAL_NAMES_KEY="disc_local_names";
 enum Speed {
  SLOW(1.35,30_000_000_000L),STANDARD(1.0,DiscRotation.PERIOD),FAST(.75,12_000_000_000L);
  private final double motionScale;
  final long rotationPeriod;
  Speed(double motionScale,long rotationPeriod){this.motionScale=motionScale;this.rotationPeriod=rotationPeriod;}
  long duration(long nanos){return Math.round(nanos*motionScale);}
 }
 record Values(int amount,boolean front,boolean rotate,boolean vinyl,Speed motionSpeed,Speed rotationSpeed,List<String> localNames){
  Values(int amount,boolean front,boolean rotate,boolean vinyl,Speed motionSpeed,Speed rotationSpeed){this(amount,front,rotate,vinyl,motionSpeed,rotationSpeed,LocalDiscService.DEFAULT_NAMES);}
  Values(int amount,boolean front,boolean rotate,boolean vinyl){this(amount,front,rotate,vinyl,Speed.STANDARD,Speed.STANDARD);}
 }
 static final Values DEFAULT=new Values(DiscExposure.DEFAULT,false,false,false);
 private final ConfigManager manager;
 private final Consumer<Values> update;
 private final AtomicLong revision=new AtomicLong();
 private final Consumer<ConfigHelper> listener=this::changed;
 private volatile boolean active=true;
 private DiscOrderDialog orderDialog;
 DiscSettings(ConfigManager manager,Consumer<Values> update){
  this(manager,update,()->"盘面来源暂不可用");
 }
 DiscSettings(ConfigManager manager,Consumer<Values> update,Supplier<String> artworkSource){
  this.manager=manager;this.update=update;
  try{
   manager.addConfigChangeListener("config.json",listener);
   update.accept(read(manager.getConfig("config.json")));
   orderDialog=new DiscOrderDialog(this,artworkSource);
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
  return new Values(amount,front,rotate,vinyl,speed(config,MOTION_SPEED_KEY),speed(config,ROTATION_SPEED_KEY),localNames(config));
 }
 List<String> order(){return localNames(manager.getConfig("config.json"));}
 synchronized boolean saveOrder(List<String> order){
  if(!active)return false;
  try{
   ConfigHelper config=manager.getConfig("config.json");
   Object previous=config.get(LOCAL_NAMES_KEY,"");
   config.set(LOCAL_NAMES_KEY,String.join(",",order));
   if(config.save()){
    changed(config);
    return true;
   }
   config.set(LOCAL_NAMES_KEY,previous);
  }catch(RuntimeException invalid){return false;}
  return false;
 }
 private static List<String> localNames(ConfigHelper config){
  try{return LocalDiscService.candidateNames(config.get(LOCAL_NAMES_KEY,""));}
  catch(RuntimeException invalid){return LocalDiscService.DEFAULT_NAMES;}
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
 @Override public synchronized void close(){
  if(!active)return;active=false;revision.incrementAndGet();
  if(orderDialog!=null){orderDialog.close();orderDialog=null;}
  try{manager.removeConfigChangeListener(listener);}
  finally{if(manager instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}}
 }
}
