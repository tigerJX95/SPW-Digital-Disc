package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import static io.github.tigerjx95.spwcdprobe.DiscSettings.*;
public class DiscSettingsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Manager implements ConfigManager,AutoCloseable {
  final ConfigHelper helper,appearance;final List<Consumer<ConfigHelper>> listeners=new ArrayList<>();int closes;
  Manager(Path file,ConfigHelper helper)throws Exception{
   this.helper=helper;
   appearance=helper(file.resolveSibling(DiscSettings.APPEARANCE_CONFIG));
  }
  public ConfigHelper getConfig(){return helper;}
  public ConfigHelper getConfig(String name){
   if(name.equals(DiscSettings.APPEARANCE_CONFIG))return appearance;
   check(name.equals("config.json"),"same config as preference schema");
   return helper;
  }
  public void addConfigChangeListener(Consumer<ConfigHelper> listener){listeners.add(listener);}
  public void addConfigChangeListener(String name,Consumer<ConfigHelper> listener){getConfig(name);listeners.add(listener);}
  public void removeConfigChangeListener(Consumer<ConfigHelper> listener){listeners.remove(listener);}
  public void close(){closes++;}
  void fire(){fire(helper);}
  void fire(ConfigHelper changed){for(var listener:List.copyOf(listeners))listener.accept(changed);}
 }
 static ConfigHelper helper(Path file)throws Exception{return (ConfigHelper)Class.forName(TestHost.helper()).getConstructor(Path.class).newInstance(file);}
 static void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
 static void appearanceSurvivesNativeSliderSave()throws Exception{
  Path file=Files.createTempDirectory("disc-appearance").resolve("config.json");
  ConfigHelper nativeSlider=helper(file);
  ConfigHelper previous=helper(file);
  previous.set(DiscSettings.STYLE_KEY,"REEL_COVER:BLUE");
  check(previous.save(),"save old-format reel selection");
  var oldSelection=new DiscSettings.Appearance(DiscSettings.Style.REEL_COVER,DiscSettings.VinylColor.BLUE);
  var newSelection=new DiscSettings.Appearance(DiscSettings.Style.REEL,DiscSettings.VinylColor.RANDOM,DiscSettings.ReelColor.ORANGE);
  var values=new ArrayList<DiscSettings.Values>();
  var manager=new Manager(file,previous);
  try(var settings=new DiscSettings(manager,values::add)){
   check(settings.appearance().equals(oldSelection),"read old-format reel selection");
   nativeSlider.set(DiscExposure.KEY,35f);
   check(nativeSlider.save(),"native slider saves its older snapshot");
   manager.fire(nativeSlider);
   flush();
   check(helper(file).get(DiscSettings.STYLE_KEY,"").isEmpty(),"older slider snapshot replaced the old style key");
   check(values.getLast().amount()==35&&values.getLast().appearance().equals(oldSelection),
       "moving the slider retains the migrated reel selection");
  }
  var current=new Manager(file,helper(file));
  try(var settings=new DiscSettings(current,values::add)){
   check(settings.appearance().equals(oldSelection),"migrated selection survives restart");
   check(settings.saveAppearance(newSelection),"save a new style and color");
   nativeSlider.set(DiscExposure.KEY,80f);
   check(nativeSlider.save(),"save exposure after changing style");
   current.fire(nativeSlider);
   flush();
   check(values.getLast().amount()==80&&values.getLast().appearance().equals(newSelection),
       "slider preserves newly saved style and color");
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),values::add)){
   check(values.getLast().amount()==80&&settings.appearance().equals(newSelection),
       "restart preserves both exposure and new appearance");
  }
 }
 static void albumOverridesSurviveRestart()throws Exception{
  Path file=Files.createTempDirectory("disc-album-styles").resolve("config.json");
  var first=DiscSettings.AlbumKey.of("专辑 A / Edition","Various Artists","Singer A",2026,"a.flac");
  var same=DiscSettings.AlbumKey.of("专辑 A / Edition","Various Artists","Singer B",2026,"b.flac");
  var other=DiscSettings.AlbumKey.of("专辑 A / Edition","Other Artist","Singer B",2026,"c.flac");
  var missing=DiscSettings.AlbumKey.of(null,null,null,0,"d.flac");
  var override=new DiscSettings.Appearance(DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE,DiscSettings.ReelColor.GOLD);
  var framing=new VinylLabelFraming(new VinylLabelFraming.Crop(90,25,110,35),false,125,.5f,-.2f);
  var framed=new DiscSettings.Appearance(DiscSettings.Style.COLORED_VINYL,DiscSettings.VinylColor.BLUE,DiscSettings.ReelColor.DEFAULT,framing);
  var global=new DiscSettings.Appearance(DiscSettings.Style.COLORED_VINYL,DiscSettings.VinylColor.RANDOM);
  var values=new ArrayList<DiscSettings.Values>();
  var manager=new Manager(file,helper(file));
  try(var settings=new DiscSettings(manager,values::add)){
   check(!settings.saveAlbumAppearance(missing,override),"no album metadata cannot create an override");
   check(settings.saveAlbumAppearance(first,override),"save album style");
   check(settings.saveAppearance(global),"change global style independently");
   check(settings.saveAlbumAppearance(other,framed),"save colored label framing per album");
   manager.helper.set(DiscExposure.KEY,75f);
   check(manager.helper.save(),"native preferences save");
   manager.fire();
   flush();
   var current=values.getLast();
   check(current.amount()==75&&current.appearanceFor(same).equals(override),"album override applies to other tracks");
   check(current.appearanceFor(other).equals(framed)&&current.appearanceFor(null).equals(global),"framing remains specific to its album");
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),values::add)){
   check(values.getLast().appearanceFor(same).equals(override),"album override survives restart");
   check(values.getLast().appearanceFor(other).equals(framed),"custom label framing survives restart");
   check(settings.saveAlbumAppearance(same,null),"clear album override");
   flush();
   check(values.getLast().appearanceFor(first).equals(global),"clearing resumes the current global style");
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),values::add)){
   check(settings.albumAppearance(first)==null,"cleared override stays cleared");
  }
  var colors=DiscSettings.ReelColor.values();
  check(colors[0]==DiscSettings.ReelColor.SILVER&&colors[1]==DiscSettings.ReelColor.GOLD
      &&colors[colors.length-1]==DiscSettings.ReelColor.RANDOM&&DiscSettings.ReelColor.DEFAULT==colors[0],
      "preserve verified reel order and default");
  System.out.println("PASS album style precedence, shared album key, independent global settings, restart and reset");
 }
 static void coverFramingSurvivesRestart()throws Exception{
  Path file=Files.createTempDirectory("disc-cover-framing").resolve("config.json");
  var legacy=new Manager(file,helper(file));
  var album=new DiscSettings.AlbumKey("专辑",null,2026,null);
  var inherited=new DiscSettings.AlbumKey("另一张专辑",null,2026,null);
  var oldFraming=new VinylLabelFraming(new VinylLabelFraming.Crop(100,100,100,100),false,128,.2f,0);
  String albumName=Base64.getEncoder().encodeToString(album.album().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  legacy.helper.set(DiscSettings.TRIM_COVER_KEY,"RIGHT");
  legacy.appearance.set(DiscSettings.ALBUM_STYLES_KEY,albumName+" - 2026 COLORED_VINYL:BLUE:SILVER~"+oldFraming.encode());
  check(legacy.helper.save()&&legacy.appearance.save(),"seed test.1 native trim and embedded album label");
  var published=new ArrayList<DiscSettings.Values>();
  var reelFraming=new VinylLabelFraming(VinylLabelFraming.Crop.FULL,false,155,0,.3f);
  var newLabel=new FramingChoice(FramingTarget.BOTH_VINYL,VinylLabelFraming.DEFAULT);
  try(var settings=new DiscSettings(legacy,published::add)){
   check(settings.coverFraming().mode()==CoverWhiteBorders.Mode.RIGHT,"old global trim retained");
   check(published.getLast().framingFor(album,Style.COLORED_VINYL).equals(oldFraming),"label imported from test.1");
   check(settings.saveAlbumAppearance(album,null),"reset album appearance before opening framing settings");
   flush();
   check(settings.albumAppearance(album)==null&&published.getLast().framingFor(album,Style.COLORED_VINYL).equals(oldFraming),"resetting style preserves framing");
  }
  var previous=new Manager(file,helper(file));
  previous.appearance.set(COVER_FRAMING_KEY,"RIGHT\n"+albumName+" - 2026 "+oldFraming.encode());
  check(previous.appearance.save(),"seed test.2 through test.4 cover framing");
  try(var settings=new DiscSettings(previous,published::add)){
   check(settings.coverFraming().forAlbum(album).forStyle(Style.BLACK_VINYL)==null,"earlier label choice remains colored-vinyl only");
   check(settings.coverFraming().forAlbum(album).forStyle(Style.COLORED_VINYL).equals(oldFraming),"migration survives style change and restart");
   check(settings.saveCoverFraming(CoverWhiteBorders.Mode.PAIRED,album,new AlbumFraming(newLabel,oldFraming,reelFraming)),"save both scopes and global crop together");
   check(settings.albumAppearance(album)==null,"independent label never creates an album style override");
   flush();
   check(published.getLast().trimCover()==CoverWhiteBorders.Mode.PAIRED&&
       published.getLast().framingFor(album,Style.COLORED_VINYL).equals(VinylLabelFraming.DEFAULT)&&
       published.getLast().framingFor(inherited,Style.COLORED_VINYL)==null,"full image is a valid override; other albums still follow");
   check(published.getLast().framingFor(album,Style.BLACK_VINYL).equals(VinylLabelFraming.DEFAULT),"both vinyl records use the label crop");
   check(published.getLast().framingFor(album,Style.CD).equals(oldFraming)&&
       published.getLast().framingFor(album,Style.REEL_COVER).equals(reelFraming)&&
       published.getLast().framingFor(album,Style.REEL)==null,"each cover disc uses its own full-face crop, excluding the solid reel");
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),published::add)){
   check(settings.coverFraming().forAlbum(album).forStyle(Style.COLORED_VINYL).equals(VinylLabelFraming.DEFAULT),"explicit full image persists");
   check(settings.saveCoverFraming(CoverWhiteBorders.Mode.PAIRED,album,new AlbumFraming(null,oldFraming,reelFraming)),"resume following large cover on vinyl only");
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),published::add)){
   check(settings.coverFraming().forAlbum(album).forStyle(Style.COLORED_VINYL)==null,"reset remains cleared after restart");
   check(settings.coverFraming().forAlbum(album).forStyle(Style.CD).equals(oldFraming),"resetting label leaves the independent full-face crop saved");
   check(settings.coverFraming().forAlbum(album).forStyle(Style.REEL_COVER).equals(reelFraming),"reel retains its own crop across restarts");
   check(settings.coverFraming().mode()==CoverWhiteBorders.Mode.PAIRED,"saved global trim persists separately from native mode");
  }
  previous=new Manager(file,helper(file));
  var legacyFaces=new StringJoiner("\n").add("RIGHT");
  for(var target:new FramingTarget[]{FramingTarget.CD,FramingTarget.REEL_COVER,FramingTarget.BOTH_DISC}){
   var title=Base64.getEncoder().encodeToString(target.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   legacyFaces.add(title+" - 2026 "+newLabel.encode()+" "+new FramingChoice(target,oldFraming).encode());
  }
  previous.appearance.set(COVER_FRAMING_KEY,legacyFaces.toString());
  check(previous.appearance.save(),"seed test.5 through test.7 face scopes");
  try(var settings=new DiscSettings(new Manager(file,helper(file)),published::add)){
   var config=settings.coverFraming();
   for(var target:new FramingTarget[]{FramingTarget.CD,FramingTarget.REEL_COVER,FramingTarget.BOTH_DISC}){
    var migrated=config.forAlbum(new DiscSettings.AlbumKey(target.name(),null,2026,null));
    check(migrated.label().equals(newLabel),"test.7 label scope survives");
    check(Objects.equals(migrated.cd(),target.accepts(Style.CD)?oldFraming:null),"test.7 CD scope survives");
    check(Objects.equals(migrated.reel(),target.accepts(Style.REEL_COVER)?oldFraming:null),"test.7 reel scope survives");
   }
  }
  var cdPreset=new FramingChoice(FramingTarget.CD,reelFraming);
  var reelPreset=new FramingChoice(FramingTarget.REEL_COVER,oldFraming);
  var blackOnly=new FramingChoice(FramingTarget.BLACK_VINYL,oldFraming);
  var blackAlbum=AlbumKey.of("Don't you see!","ZARD",null,1997,null);
  var otherBlackAlbum=AlbumKey.of("黑胶专辑",null,"艺人",2026,null);
  var presets=Map.of("封面\n示例",cdPreset,"磁带预设",reelPreset);
  try(var settings=new DiscSettings(new Manager(file,helper(file)),published::add)){
   var config=settings.coverFraming();
   var first=config.forAlbum(album);
   var second=config.forAlbum(inherited);
   check(settings.saveCoverFraming(CoverWhiteBorders.Mode.PAIRED,
       Map.of(album,new AlbumFraming(first.label(),reelFraming,first.reel()),
           inherited,new AlbumFraming(second.label(),second.cd(),oldFraming),
           blackAlbum,new AlbumFraming(blackOnly,null,null),
           otherBlackAlbum,new AlbumFraming(blackOnly,null,null)),
       presets),"save four albums and named presets together");
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),published::add)){
   var config=settings.coverFraming();
   check(config.presets().equals(presets),"presets round-trip without damaging names");
   check(config.forAlbum(album).cd().equals(reelFraming),"confirmation saves the first album draft");
   check(config.forAlbum(inherited).reel().equals(oldFraming),"confirmation saves the second album draft");
   check(config.forAlbum(inherited).cd()==null,"confirmation saves only the edited style");
   for(var key:List.of(blackAlbum,otherBlackAlbum)){
    var saved=config.forAlbum(key);
    check(oldFraming.equals(saved.forStyle(Style.BLACK_VINYL))&&saved.forStyle(Style.COLORED_VINYL)==null,
        "each album retains the black-only crop after restart");
   }
   check(config.forAlbum(new AlbumKey(FramingTarget.BOTH_DISC.name(),null,2026,null)).reel().equals(oldFraming),
       "confirmation preserves albums outside the edited drafts");
  }
  System.out.println("PASS combined cover settings, migration, persistence and independent album framing");
 }

 static void largeAlbumSettingsSaveWithoutBlockingUi()throws Exception{
  Path file=Files.createTempDirectory("disc-large-album-styles").resolve("config.json");
  int count=10_000;
  var keys=new ArrayList<DiscSettings.AlbumKey>(count);
  var records=new StringJoiner("\n");
  var encoder=Base64.getEncoder();
  var silver=new DiscSettings.Appearance(DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE);
  var random=new DiscSettings.Appearance(DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE,DiscSettings.ReelColor.RANDOM);
  for(int index=0;index<count;index++){
   String title="专辑 "+index;
   keys.add(new DiscSettings.AlbumKey(title,null,2026,null));
   records.add(encoder.encodeToString(title.getBytes(java.nio.charset.StandardCharsets.UTF_8))+" - 2026 REEL:BLUE");
  }
  var seed=new Manager(file,helper(file));
  seed.appearance.set(DiscSettings.ALBUM_STYLES_KEY,records.toString());
  check(seed.appearance.save(),"seed a configuration with 10,000 overrides");
  var values=new ArrayList<DiscSettings.Values>();
  long start=System.nanoTime();
  var manager=new Manager(file,helper(file));
  try(var settings=new DiscSettings(manager,values::add)){
   double loadMs=(System.nanoTime()-start)/1_000_000.0;
   var current=values.getLast();
   check(current.albumStyles().size()==count,"all overrides loaded");
   start=System.nanoTime();
   for(int index=0;index<100_000;index++)
    check(current.appearanceFor(keys.get(index%count)).equals(silver),"lookup among all stored albums");
   double lookupMs=(System.nanoTime()-start)/1_000_000.0;

   var started=new CountDownLatch(1);
   var release=new CountDownLatch(1);
   var finished=new CountDownLatch(1);
   var selected=keys.getLast();
   long[] saveNanos={0};
   try{
    SwingUtilities.invokeAndWait(()->DiscAppearanceDialog.runSave(()->{
     if(SwingUtilities.isEventDispatchThread())return false;
     started.countDown();
     try{if(!release.await(5,TimeUnit.SECONDS))return false;}
     catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}
     long before=System.nanoTime();
     boolean saved=settings.saveAlbumAppearance(selected,random);
     saveNanos[0]=System.nanoTime()-before;
     return saved;
    },saved->{
     if(saved&&SwingUtilities.isEventDispatchThread())finished.countDown();
    }));
    check(started.await(5,TimeUnit.SECONDS),"save starts off the EDT");
    SwingUtilities.invokeAndWait(()->check(settings.albumAppearance(selected).equals(silver),
        "EDT remains responsive during an in-flight save"));
   }finally{release.countDown();}
   check(finished.await(5,TimeUnit.SECONDS),"successful save completes on the EDT");
   flush();
   check(values.getLast().appearanceFor(selected).equals(random),"published snapshot includes the new selection");
   System.out.printf(Locale.ROOT,"10,000 overrides: load %.1f ms, worker save %.1f ms, 100,000 lookups %.1f ms%n",
       loadMs,saveNanos[0]/1_000_000.0,lookupMs);
  }
  try(var settings=new DiscSettings(new Manager(file,helper(file)),value->{})){
   check(settings.albumAppearance(keys.getLast()).equals(random),"random selection persists after restarting");
   check(settings.albumAppearance(keys.getFirst()).equals(silver),"other selections are preserved");
  }
 }

 private static void orderMigrationAndFailedWrite()throws Exception{
  Path file=Files.createTempDirectory("disc-order-migration").resolve("config.json");
  var runtime=helper(file);
  var oldOrder=List.of("disc.jpg","disc.png","cd.jpg","cd.png");
  var newOrder=List.of("cd.jpg","disc.png","cd.png","disc.jpg");
  String original=String.join(",",oldOrder);
  runtime.set(DiscSettings.LOCAL_NAMES_KEY,original);
  check(runtime.save(),"prior release can persist a custom search order");
  var staleNative=helper(file);
  var revisions=new ArrayList<DiscSettings.Values>();
  var manager=new Manager(file,runtime);
  try(var settings=new DiscSettings(manager,revisions::add)){
   check(settings.order().equals(oldOrder)&&manager.appearance.get(DiscSettings.LOCAL_NAMES_KEY,"").equals(original),
       "legacy lookup order migrates on start");
   check(settings.saveOrder(newOrder),"new lookup order saves to the plugin file");
   staleNative.set(DiscExposure.KEY,75f);check(staleNative.save(),"native slider saves a stale runtime snapshot");
   manager.fire(staleNative);flush();
   check(revisions.getLast().amount()==75&&revisions.getLast().localNames().equals(newOrder),
       "native file changes still apply while the independent order survives");
  }
  var restarted=new Manager(file,helper(file));
  var failing=(ConfigHelper)java.lang.reflect.Proxy.newProxyInstance(ConfigHelper.class.getClassLoader(),
      new Class<?>[]{ConfigHelper.class},(proxy,method,args)->{
       if(method.getName().equals("save"))throw new IllegalStateException("injected write failure");
       return method.invoke(restarted.appearance,args);
      });
  try(var settings=new DiscSettings(new Manager(file,restarted.helper){
   @Override public ConfigHelper getConfig(String name){
    return name.equals(DiscSettings.APPEARANCE_CONFIG)?failing:super.getConfig(name);
   }
  },value->check(value.localNames().equals(newOrder),"restart uses the independent order"))){
   check(!settings.saveOrder(oldOrder)&&settings.order().equals(newOrder),
       "a throwing save leaves the editor and published order on the saved value");
   check(restarted.appearance.get(DiscSettings.LOCAL_NAMES_KEY,"").equals(String.join(",",newOrder)),
       "a throwing save also restores the helper's in-memory value");
  }
  System.out.println("PASS lookup order migration, stale host snapshot isolation and exception rollback");
 }

 public static void main(String[] args)throws Exception{
  Path file=Files.createTempDirectory("disc-settings").resolve("config.json");
  ConfigHelper config=helper(file);Manager manager=new Manager(file,config);var values=new ArrayList<Integer>();
  DiscSettings settings=new DiscSettings(manager,v->values.add(v.amount()));check(values.equals(List.of(50)),"fresh profile preserves default view");
  config.set(DiscExposure.KEY,75.25f);check(config.save(),"actual host ConfigHelper saves persisted value");
  check(DiscExposure.percent(helper(file).get(DiscExposure.KEY,50f))==75,"fresh host helper restores saved value");
  // Changes happen inside one EDT turn so only the final queued value is applied.
  SwingUtilities.invokeAndWait(()->{config.set(DiscExposure.KEY,10f);manager.fire();config.set(DiscExposure.KEY,90f);manager.fire();});flush();
  check(values.equals(List.of(50,90)),"rapid queued changes coalesce to newest value");
  config.set(DiscExposure.KEY,"bad");manager.fire();flush();check(values.get(values.size()-1)==50,"malformed stored value returns to default");
  config.set(DiscExposure.KEY,500f);manager.fire();flush();check(values.get(values.size()-1)==100,"out-of-range value clamped");
  var order=List.of("disc.png","cd.jpg","cd.png","disc.jpg");
  check(settings.saveOrder(order),"save ordered filenames using host storage");
  flush();
  check(settings.order().equals(order),"editor reloads saved order");
  int before=values.size();var late=manager.listeners.get(0);
  SwingUtilities.invokeAndWait(()->{config.set(DiscExposure.KEY,0f);manager.fire();settings.close();});flush();
  late.accept(config);flush();check(values.size()==before&&manager.listeners.isEmpty()&&manager.closes==1,"close removes listener, closes owned manager and ignores queued/late updates");
  settings.close();check(manager.closes==1,"idempotent close");
  Manager restored=new Manager(file,helper(file));var restart=new ArrayList<Integer>();
  try(DiscSettings next=new DiscSettings(restored,v->{restart.add(v.amount());check(v.localNames().getFirst().equals("disc.png"),"restore ordered filenames");})){check(restart.equals(List.of(75)),"saving search priority leaves unsaved runtime settings out of the host file");}
  appearanceSurvivesNativeSliderSave();
  orderMigrationAndFailedWrite();
  albumOverridesSurviveRestart();
  coverFramingSurvivesRestart();
  largeAlbumSettingsSaveWithoutBlockingUi();
  var random=new DiscSettings.ReelRandom();
  DiscSettings.ReelColor previous=null;
  for(int track=0;track<48;track++){
   String album="Album "+track;
   var first=DiscSettings.AlbumKey.of(album,"Album Artist","Singer A",2026,"first.flac");
   var second=DiscSettings.AlbumKey.of(album,"Album Artist","Singer B",2026,"second.flac");
   var color=random.resolve(first);
   check(DiscSettings.ReelColor.PALETTE.size()==6&&DiscSettings.ReelColor.PALETTE.contains(color),
       "random picks one of six reel colors");
   check(color!=previous,"changing album changes reel color");
   check(color==random.resolve(second),"tracks in one album share the color");
   previous=color;
  }
  System.out.println("PASS reel palette, same-album color stability, and no repeat on album switch");
  System.out.println("PASS appearance migration and persistence with stale native slider writes");
  System.out.println("PASS actual host ConfigHelper default/save/reload, settings-key integration, newest EDT publication, malformed and range validation, queued-callback cancellation, exact listener/manager cleanup and saved restart value");
 }
}
