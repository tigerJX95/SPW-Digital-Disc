package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
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
  try(DiscSettings next=new DiscSettings(restored,v->{restart.add(v.amount());check(v.localNames().getFirst().equals("disc.png"),"restore ordered filenames");})){check(restart.equals(List.of(100)),"restart restores saved exposure and filenames rather than unsaved slider changes");}
  appearanceSurvivesNativeSliderSave();
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
