package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.config.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
public class DiscSettingsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Manager implements ConfigManager,AutoCloseable {
  final ConfigHelper helper;final List<Consumer<ConfigHelper>> listeners=new ArrayList<>();int closes;
  Manager(ConfigHelper helper){this.helper=helper;}
  public ConfigHelper getConfig(){return helper;}
  public ConfigHelper getConfig(String name){check(name.equals("config.json"),"same config as preference schema");return helper;}
  public void addConfigChangeListener(Consumer<ConfigHelper> listener){listeners.add(listener);}
  public void addConfigChangeListener(String name,Consumer<ConfigHelper> listener){getConfig(name);listeners.add(listener);}
  public void removeConfigChangeListener(Consumer<ConfigHelper> listener){listeners.remove(listener);}
  public void close(){closes++;}
  void fire(){for(var listener:List.copyOf(listeners))listener.accept(helper);}
 }
 static ConfigHelper helper(Path file)throws Exception{return (ConfigHelper)Class.forName(TestHost.helper()).getConstructor(Path.class).newInstance(file);}
 static void flush()throws Exception{SwingUtilities.invokeAndWait(()->{});}
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempDirectory("disc-settings").resolve("config.json");
  ConfigHelper config=helper(file);Manager manager=new Manager(config);var values=new ArrayList<Integer>();
  DiscSettings settings=new DiscSettings(manager,v->values.add(v.amount()));check(values.equals(List.of(50)),"fresh profile preserves default view");
  config.set(DiscExposure.KEY,75.25f);check(config.save(),"actual host ConfigHelper saves persisted value");
  check(DiscExposure.percent(helper(file).get(DiscExposure.KEY,50f))==75,"fresh host helper restores saved value");
  // Changes happen inside one EDT turn so only the final queued value is applied.
  SwingUtilities.invokeAndWait(()->{config.set(DiscExposure.KEY,10f);manager.fire();config.set(DiscExposure.KEY,90f);manager.fire();});flush();
  check(values.equals(List.of(50,90)),"rapid queued changes coalesce to newest value");
  config.set(DiscExposure.KEY,"bad");manager.fire();flush();check(values.get(values.size()-1)==50,"malformed stored value returns to default");
  config.set(DiscExposure.KEY,500f);manager.fire();flush();check(values.get(values.size()-1)==100,"out-of-range value clamped");
  int before=values.size();var late=manager.listeners.get(0);
  SwingUtilities.invokeAndWait(()->{config.set(DiscExposure.KEY,0f);manager.fire();settings.close();});flush();
  late.accept(config);flush();check(values.size()==before&&manager.listeners.isEmpty()&&manager.closes==1,"close removes listener, closes owned manager and ignores queued/late updates");
  settings.close();check(manager.closes==1,"idempotent close");
  Manager restored=new Manager(helper(file));var restart=new ArrayList<Integer>();
  try(DiscSettings next=new DiscSettings(restored,v->restart.add(v.amount()))){check(restart.equals(List.of(75)),"restart restores saved value rather than unsaved slider changes");}
  System.out.println("PASS actual host ConfigHelper default/save/reload, settings-key integration, newest EDT publication, malformed and range validation, queued-callback cancellation, exact listener/manager cleanup and saved restart value");
 }
}
