package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import javax.swing.SwingUtilities;
/** Uses the host's persisted settings; only UI-state publication runs on the EDT. */
final class DiscSettings implements AutoCloseable {
 static final String FRONT_KEY="disc_in_front";
 static final String ROTATE_KEY="disc_rotation";
 static final String VINYL_KEY="disc_vinyl";
 static final String STYLE_KEY="disc_style";
 static final String ALBUM_STYLES_KEY="album_styles";
 static final String APPEARANCE_CONFIG="disc_appearance.json";
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
   int key=AlbumKey.of(album,albumArtist,artist,year,path).colorHash();
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
 enum ReelColor {
  SILVER("拉丝银",0xa7a8ab,0xb5b7bb,55), GOLD("香槟金",0xc9a879,0xc9aa79,35),
  ORANGE("活力橙",0xff6710,0xcd9175,12), WHITE("象牙白",0xf5f4f1,0xaaaeb1,3),
  GRAPHITE("石墨灰",0x484a50,0x787b80,37), WINE("深酒红",0x682332,0x9a4355,35),
  RANDOM("随机色",0xff6710,0xcd9175,12);
  static final ReelColor DEFAULT=SILVER;
  static final List<ReelColor> PALETTE=List.of(ORANGE,WHITE,SILVER,GOLD,GRAPHITE,WINE);
  final String label;
  final int rgb, trim, sheen;
  ReelColor(String label,int rgb,int trim,int sheen){
   this.label=label;this.rgb=rgb;this.trim=trim;this.sheen=sheen;
  }
 }
 record AlbumKey(String album,String artist,int year,String path){
  static AlbumKey of(String album,String albumArtist,String artist,int year,String path){
   return album==null||album.isBlank()?new AlbumKey(null,null,0,path):
    new AlbumKey(album,albumArtist==null||albumArtist.isBlank()?artist:albumArtist,year,null);
  }
  int colorHash(){return album==null?Objects.hashCode(path):Objects.hash(album,artist,year);}
 }
 static final class ReelRandom {
  private AlbumKey album;
  private ReelColor color;
  synchronized ReelColor resolve(AlbumKey next){
   if(!next.equals(album)){
    var palette=ReelColor.PALETTE;
    int previous=palette.indexOf(color==null?ReelColor.RANDOM:color);
    int pick=java.util.concurrent.ThreadLocalRandom.current().nextInt(palette.size()-(previous<0?0:1));
    if(previous>=0&&pick>=previous)pick++;
    color=palette.get(pick);
    album=next;
   }
   return color;
  }
 }
 enum Style {
  CD("专辑封面 CD"), BLACK_VINYL("经典黑胶唱片"), COLORED_VINYL("彩胶唱片"), REEL("开盘磁带"), REEL_COVER("专辑封面卷盘");
  final String label;
  Style(String label){this.label=label;}
  boolean reel(){return this==REEL||this==REEL_COVER;}
  boolean vinyl(){return this==BLACK_VINYL||this==COLORED_VINYL;}
 }
 enum ArtworkKind { CUSTOM, GENERATED, UNKNOWN }
 record ArtworkSource(String description,ArtworkKind kind){
  ArtworkSource(String description){this(description,ArtworkKind.UNKNOWN);}
  boolean customImage(){return kind==ArtworkKind.CUSTOM;}
 }
 record Appearance(Style style,VinylColor color,ReelColor reelColor){
  Appearance(Style style,VinylColor color){this(style,color,ReelColor.DEFAULT);}
  Appearance {
   if(color==VinylColor.BLACK)color=VinylColor.BLUE;
  }
  VinylColor finish(){return style==Style.COLORED_VINYL?color:VinylColor.BLACK;}
 }
 record Values(int amount,boolean front,boolean rotate,Appearance appearance,Speed motionSpeed,Speed rotationSpeed,List<String> localNames,CoverWhiteBorders.Mode trimCover,Map<AlbumKey,Appearance> albumStyles){
  Values(int amount,boolean front,boolean rotate,boolean vinyl,Speed motionSpeed,Speed rotationSpeed){
   this(amount,front,rotate,new Appearance(vinyl?Style.BLACK_VINYL:Style.CD,VinylColor.BLUE),motionSpeed,rotationSpeed,LocalDiscService.DEFAULT_NAMES,CoverWhiteBorders.Mode.OFF,Map.of());
  }
  Values(int amount,boolean front,boolean rotate,boolean vinyl){this(amount,front,rotate,vinyl,Speed.STANDARD,Speed.STANDARD);}
  Appearance appearanceFor(AlbumKey album){return album==null?appearance:albumStyles.getOrDefault(album,appearance);}
  boolean vinyl(){return appearance.style().vinyl();}
  VinylColor vinylColor(){return appearance.finish();}
 }
 static final Values DEFAULT=new Values(DiscExposure.DEFAULT,false,false,false);
 private final ConfigManager manager;
 private final ConfigHelper appearanceConfig;
 private volatile Map<AlbumKey,Appearance> albumStyles=Map.of();
 private volatile List<String> localOrder=LocalDiscService.DEFAULT_NAMES;
 private final Consumer<Values> update;
 private final AtomicLong revision=new AtomicLong();
 private final Consumer<ConfigHelper> listener=this::changed;
 private volatile boolean active=true;
 private DiscOrderDialog orderDialog;
 private DiscAppearanceDialog appearanceDialog;
 DiscSettings(ConfigManager manager,Consumer<Values> update){
  this(manager,update,()->new ArtworkSource("盘面来源暂不可用"),()->null);
 }
 DiscSettings(ConfigManager manager,Consumer<Values> update,Supplier<ArtworkSource> artworkSource,Supplier<AlbumKey> playingAlbum){
  this(manager,update,artworkSource,playingAlbum,appearance->{});
 }
 DiscSettings(ConfigManager manager,Consumer<Values> update,Supplier<ArtworkSource> artworkSource,Supplier<AlbumKey> playingAlbum,
     Consumer<Appearance> prepareDefault){
  this.manager=manager;this.update=update;
  try{
   appearanceConfig=manager.getConfig(APPEARANCE_CONFIG);
   var runtimeConfig=manager.getConfig("config.json");
   migrateAppearance(runtimeConfig);
   albumStyles=readAlbumStyles();
   localOrder=readOrder(runtimeConfig);
   manager.addConfigChangeListener("config.json",listener);
   update.accept(read(runtimeConfig));
   orderDialog=new DiscOrderDialog(this,artworkSource);
   appearanceDialog=new DiscAppearanceDialog(this,playingAlbum,artworkSource,prepareDefault);
  }catch(RuntimeException error){close();throw error;}
 }
 private Values read(ConfigHelper config){
  int amount;boolean front;
  try{amount=DiscExposure.percent(config.get(DiscExposure.KEY,50f));}
  catch(RuntimeException invalid){amount=DiscExposure.DEFAULT;}
  try{Object value=config.get(FRONT_KEY,false);front=Boolean.TRUE.equals(value)||value instanceof String s&&s.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){front=false;}
  boolean rotate;
  try{Object value=config.get(ROTATE_KEY,false);rotate=Boolean.TRUE.equals(value)||value instanceof String text&&text.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){rotate=false;}
  return new Values(amount,front,rotate,appearance(config),speed(config,MOTION_SPEED_KEY),speed(config,ROTATION_SPEED_KEY),localOrder,trimCover(config),albumStyles);
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
 private Appearance appearance(ConfigHelper config){
  Appearance saved=savedAppearance(appearanceConfig);
  if(saved!=null)return saved;
  return previousAppearance(config);
 }
 private static Appearance savedAppearance(ConfigHelper config){
  try{return decodeAppearance(config.get(STYLE_KEY,""));}
  catch(RuntimeException invalid){return null;}
 }
 private static Appearance decodeAppearance(String value){
  String[] saved=value.split(":",-1);
  if(saved.length!=2&&saved.length!=3)return null;
  return new Appearance(Style.valueOf(saved[0]),VinylColor.valueOf(saved[1]),
      saved.length==3?parseReelColor(saved[2]):ReelColor.DEFAULT);
 }
 private static String encodeAppearance(Appearance appearance){
  String encoded=appearance.style().name()+":"+appearance.color().name();
  return appearance.reelColor()==ReelColor.DEFAULT?encoded:encoded+":"+appearance.reelColor().name();
 }
 Appearance albumAppearance(AlbumKey album){return albumStyles.get(album);}
 synchronized boolean saveAlbumAppearance(AlbumKey album,Appearance appearance){
  if(!active||album==null||album.album()==null)return false;
  if(Objects.equals(appearance,albumStyles.get(album)))return true;
  var next=new HashMap<>(albumStyles);
  if(appearance==null)next.remove(album);else next.put(album,appearance);
  var records=new StringJoiner("\n");
  next.forEach((key,value)->records.add(packAlbumField(key.album())+" "+packAlbumField(key.artist())+" "
      +key.year()+" "+encodeAppearance(value)));
  ConfigHelper runtimeConfig;
  Map<AlbumKey,Appearance> snapshot=Map.copyOf(next);
  try{
   // Read runtime settings before committing so an unavailable config
   // cannot make an already persisted album update look like a failed save.
   runtimeConfig=manager.getConfig("config.json");
   String previous=appearanceConfig.get(ALBUM_STYLES_KEY,"");
   appearanceConfig.set(ALBUM_STYLES_KEY,records.toString());
   boolean saved;
   try{saved=appearanceConfig.save();}
   catch(RuntimeException invalid){saved=false;}
   if(!saved){
    appearanceConfig.set(ALBUM_STYLES_KEY,previous);
    return false;
   }
  }catch(RuntimeException invalid){return false;}
  albumStyles=snapshot;
  changed(runtimeConfig);
  return true;
 }
 private Map<AlbumKey,Appearance> readAlbumStyles(){
  var records=new HashMap<AlbumKey,Appearance>();
  String stored;
  try{stored=appearanceConfig.get(ALBUM_STYLES_KEY,"");}
  catch(RuntimeException invalid){return Map.of();}
  for(String record:stored.split("\n"))try{
   String[] fields=record.split(" ",-1);
   if(fields.length!=4)continue;
   String title=unpackAlbumField(fields[0]);
   Appearance appearance=decodeAppearance(fields[3]);
   if(!title.isBlank()&&appearance!=null){
    String artist=fields[1].equals("-")?null:unpackAlbumField(fields[1]);
    records.put(new AlbumKey(title,artist,Integer.parseInt(fields[2]),null),appearance);
   }
  }catch(IllegalArgumentException invalid){ /* Ignore just the damaged record. */ }
  return Map.copyOf(records);
 }
 private static String packAlbumField(String text){
  return text==null?"-":Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
 }
 private static String unpackAlbumField(String text){
  return new String(Base64.getDecoder().decode(text),StandardCharsets.UTF_8);
 }
 private static ReelColor parseReelColor(String value){
  try{return ReelColor.valueOf(value);}catch(IllegalArgumentException invalid){return ReelColor.DEFAULT;}
 }
 private static Appearance previousAppearance(ConfigHelper config){
  Appearance saved=savedAppearance(config);
  if(saved!=null)return saved;
  boolean vinyl;
  try{Object value=config.get(VINYL_KEY,false);vinyl=Boolean.TRUE.equals(value)||value instanceof String text&&text.equalsIgnoreCase("true");}
  catch(RuntimeException invalid){vinyl=false;}
  VinylColor color=vinylColor(config);
  Style style=!vinyl?Style.CD:color==VinylColor.BLACK?Style.BLACK_VINYL:Style.COLORED_VINYL;
  return new Appearance(style,color);
 }
 private void migrateAppearance(ConfigHelper previous){
  if(savedAppearance(appearanceConfig)!=null)return;
  // Native preferences can save an older snapshot of config.json.
  // Copy existing choices before that file can be rewritten.
  try{
   if(savedAppearance(previous)!=null||!previous.get(VINYL_KEY,"").isEmpty()
       ||!previous.get(VINYL_COLOR_KEY,"").isEmpty()){
    persistAppearance(previousAppearance(previous));
   }
  }catch(RuntimeException invalid){ /* Use the legacy read path if migration fails. */ }
 }
 synchronized boolean saveAppearance(Appearance appearance){
  if(!active||!persistAppearance(appearance))return false;
  changed(manager.getConfig("config.json"));
  return true;
 }
 private boolean persistAppearance(Appearance appearance){
  return persistSetting(STYLE_KEY,encodeAppearance(appearance));
 }
 private boolean persistSetting(String key,String value){
  try{
   Object previous=appearanceConfig.get(key,"");
   appearanceConfig.set(key,value);
   try{
    if(appearanceConfig.save())return true;
   }catch(RuntimeException invalid){ /* Restore the previous value below. */ }
   appearanceConfig.set(key,previous);
  }catch(RuntimeException invalid){return false;}
  return false;
 }
 List<String> order(){return localOrder;}
 synchronized boolean saveOrder(List<String> order){
  if(!active)return false;
  try{
   var next=LocalDiscService.candidateNames(String.join(",",order));
   var runtimeConfig=manager.getConfig("config.json");
   if(!persistSetting(LOCAL_NAMES_KEY,String.join(",",next)))return false;
   localOrder=next;
   changed(runtimeConfig);
   return true;
  }catch(RuntimeException invalid){return false;}
 }
 private List<String> readOrder(ConfigHelper runtimeConfig){
  String saved=storedOrder(appearanceConfig);
  if(!saved.isBlank())return LocalDiscService.candidateNames(saved);
  String legacy=storedOrder(runtimeConfig);
  var order=LocalDiscService.candidateNames(legacy);
  if(!legacy.isBlank())persistSetting(LOCAL_NAMES_KEY,String.join(",",order));
  return order;
 }
 private static String storedOrder(ConfigHelper config){
  try{return Objects.requireNonNullElse(config.get(LOCAL_NAMES_KEY,""),"");}
  catch(RuntimeException invalid){return "";}
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
