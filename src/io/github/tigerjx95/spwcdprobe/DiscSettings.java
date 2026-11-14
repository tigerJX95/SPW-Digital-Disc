package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javax.swing.SwingUtilities;
/** Uses the host's persisted settings; only UI-state publication runs on the EDT. */
final class DiscSettings implements AutoCloseable {
 static final String FRONT_KEY="disc_in_front";
 static final String ROTATE_KEY="disc_rotation";
 static final String VINYL_KEY="disc_vinyl";
 static final String STYLE_KEY="disc_style";
 static final String VINYL_COLOR_KEY="disc_vinyl_color";
 static final String MOTION_SPEED_KEY="disc_motion_speed";
 static final String TRIM_COVER_KEY="cover_white_border_mode";
 static final String LEGACY_TRIM_COVER_KEY="trim_cover_white_borders";
 static final String ROTATION_SPEED_KEY="disc_rotation_speed";
 static final String LOCAL_NAMES_KEY="disc_local_names";
 enum Speed {
  SLOW(1.35,30_000_000_000L),STANDARD(1.0,DiscRotation.PERIOD),FAST(.75,12_000_000_000L);
  private final double motionScale;
  final long rotationPeriod;
  Speed(double motionScale,long rotationPeriod){this.motionScale=motionScale;this.rotationPeriod=rotationPeriod;}
  long duration(long nanos){return Math.round(nanos*motionScale);}
 }
 enum VinylColor {
  BLACK("黑色",0x555555), BLUE("海洋蓝",0x25b5d6), GREEN("翡翠绿",0x4ec68b),
  AMBER("琥珀金",0xf3b950), PURPLE("鸢尾紫",0xb98de1), PINK("樱花粉",0xf4a8cb), RED("玫瑰红",0xd25970), RANDOM("随机色",0x25b5d6);
  private static final List<VinylColor> PALETTE=List.of(BLUE,GREEN,AMBER,PURPLE,PINK,RED);
  VinylColor resolve(String album,String albumArtist,String artist,int year,String path,int salt){
   if(this!=RANDOM)return this;
   int key=album==null||album.isBlank()?Objects.hashCode(path):
    Objects.hash(album,albumArtist==null||albumArtist.isBlank()?artist:albumArtist,year);
   int mixed=(key^salt)*0x9e3779b9;
   return PALETTE.get(Math.floorMod(mixed^(mixed>>>16),PALETTE.size()));
  }
  final String label;
  private final int rgb;
  VinylColor(String label,int rgb){this.label=label;this.rgb=rgb;}
  int shade(int level){
   int red=(rgb>>16&255)*level/85;
   int green=(rgb>>8&255)*level/85;
   int blue=(rgb&255)*level/85;
   return 0xff000000|red<<16|green<<8|blue;
  }
 }
 enum Style {
  CD("专辑封面 CD"), BLACK_VINYL("经典黑胶唱片"), COLORED_VINYL("彩胶唱片");
  final String label;
  Style(String label){this.label=label;}
 }
 record Appearance(Style style,VinylColor color){
  Appearance {
   if(color==VinylColor.BLACK)color=VinylColor.BLUE;
  }
  VinylColor finish(){return style==Style.COLORED_VINYL?color:VinylColor.BLACK;}
 }
 record Values(int amount,boolean front,boolean rotate,Appearance appearance,Speed motionSpeed,Speed rotationSpeed,List<String> localNames,CoverWhiteBorders.Mode trimCover){
  Values(int amount,boolean front,boolean rotate,boolean vinyl,Speed motionSpeed,Speed rotationSpeed){
   this(amount,front,rotate,new Appearance(vinyl?Style.BLACK_VINYL:Style.CD,VinylColor.BLUE),motionSpeed,rotationSpeed,LocalDiscService.DEFAULT_NAMES,CoverWhiteBorders.Mode.OFF);
  }
  Values(int amount,boolean front,boolean rotate,boolean vinyl){this(amount,front,rotate,vinyl,Speed.STANDARD,Speed.STANDARD);}
  boolean vinyl(){return appearance.style()!=Style.CD;}
  VinylColor vinylColor(){return appearance.finish();}
 }
 static final Values DEFAULT=new Values(DiscExposure.DEFAULT,false,false,false);
 private final ConfigManager manager;
 private final Consumer<Values> update;
 private final AtomicLong revision=new AtomicLong();
 private final Consumer<ConfigHelper> listener=this::changed;
 private volatile boolean active=true;
 private DiscOrderDialog orderDialog;
 private DiscAppearanceDialog appearanceDialog;
 DiscSettings(ConfigManager manager,Consumer<Values> update){
  this(manager,update,()->"盘面来源暂不可用");
 }
 DiscSettings(ConfigManager manager,Consumer<Values> update,Supplier<String> artworkSource){
  this.manager=manager;this.update=update;
  try{
   manager.addConfigChangeListener("config.json",listener);
   update.accept(read(manager.getConfig("config.json")));
   orderDialog=new DiscOrderDialog(this,artworkSource);
   appearanceDialog=new DiscAppearanceDialog(this);
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
  return new Values(amount,front,rotate,appearance(config),speed(config,MOTION_SPEED_KEY),speed(config,ROTATION_SPEED_KEY),localNames(config),trimCover(config));
 }
 static CoverWhiteBorders.Mode trimCover(ConfigHelper config){
  try{
   String mode=config.get(TRIM_COVER_KEY,"");
   if(!mode.isEmpty())return CoverWhiteBorders.Mode.valueOf(mode);
   return Boolean.parseBoolean(String.valueOf(config.get(LEGACY_TRIM_COVER_KEY,false)))
     ?CoverWhiteBorders.Mode.PAIRED:CoverWhiteBorders.Mode.OFF;
  }catch(RuntimeException invalid){return CoverWhiteBorders.Mode.OFF;}
 }
 Appearance appearance(){return appearance(manager.getConfig("config.json"));}
 private static Appearance appearance(ConfigHelper config){
  try{
   String[] saved=config.get(STYLE_KEY,"").split(":",-1);
   if(saved.length==2)return new Appearance(Style.valueOf(saved[0]),VinylColor.valueOf(saved[1]));
  }catch(RuntimeException invalid){ /* Read the previous format. */ }
  boolean vinyl;
  try{Object value=config.get(VINYL_KEY,false);vinyl=Boolean.TRUE.equals(value)||value instanceof String text&&text.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){vinyl=false;}
  VinylColor color=vinylColor(config);
  Style style=!vinyl?Style.CD:color==VinylColor.BLACK?Style.BLACK_VINYL:Style.COLORED_VINYL;
  return new Appearance(style,color);
 }
 synchronized boolean saveAppearance(Appearance appearance){
  if(!active)return false;
  try{
   ConfigHelper config=manager.getConfig("config.json");
   Object previous=config.get(STYLE_KEY,"");
   config.set(STYLE_KEY,appearance.style().name()+":"+appearance.color().name());
   try{
    if(config.save()){
     changed(config);
     return true;
    }
   }catch(RuntimeException invalid){ /* Restore the previous selection below. */ }
   config.set(STYLE_KEY,previous);
  }catch(RuntimeException invalid){return false;}
  return false;
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
 private static VinylColor vinylColor(ConfigHelper config){
  try{return VinylColor.valueOf(config.get(VINYL_COLOR_KEY,VinylColor.BLACK.name()));}
  catch(RuntimeException invalid){return VinylColor.BLACK;}
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
  if(appearanceDialog!=null){appearanceDialog.close();appearanceDialog=null;}
  if(orderDialog!=null){orderDialog.close();orderDialog=null;}
  try{manager.removeConfigChangeListener(listener);}
  finally{if(manager instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}}
 }
}
