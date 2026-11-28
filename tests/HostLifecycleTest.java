package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.drawscope.DrawScope;
import com.xuncorp.spw.workshop.api.Channel;
import com.xuncorp.spw.workshop.api.PluginContext;
import com.xuncorp.spw.workshop.api.config.ConfigHelper;
import com.xuncorp.spw.workshop.api.WorkshopApi;
import com.xuncorp.spw.workshop.api.hook.*;
import java.lang.reflect.Proxy;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class HostLifecycleTest {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static Field field(Object target,String name)throws Exception{
  Field result=target.getClass().getDeclaredField(name);result.setAccessible(true);return result;
 }
 private static void settings(Path dir)throws Exception{
  Path config=dir.resolve("config.json");
  var failure=new NoSuchMethodError("simulated host ABI mismatch");
  var manager=new DiscSettingsTest.Manager(config,DiscSettingsTest.helper(config)){
   @Override public void addConfigChangeListener(String name,Consumer<ConfigHelper> listener){
    super.addConfigChangeListener(name,listener);throw failure;
   }
   @Override public void removeConfigChangeListener(Consumer<ConfigHelper> listener){
    super.removeConfigChangeListener(listener);throw new IllegalStateException("cleanup");
   }
  };
  try{new DiscSettings(manager,value->{});throw new AssertionError("expected registration failure");}
  catch(NoSuchMethodError error){check(error==failure,"retain original startup error");}
  check(manager.listeners.isEmpty()&&manager.closes==1,"partially registered listener and manager must be released");
  check(failure.getSuppressed().length==1,"retain secondary cleanup error");
  var next=new DiscSettingsTest.Manager(config,DiscSettingsTest.helper(config));
  try(var settings=new DiscSettings(next,value->{})){check(next.listeners.size()==1,"one listener on fresh enable");}
  check(next.listeners.isEmpty()&&next.closes==1,"normal disable");
 }
 @SuppressWarnings("unchecked")
 private static void stop(Path dir)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","test",dir.toString(),"1.19.3",Channel.Steam));
  var hooks=(List<HookHandle>)field(plugin,"handles").get(plugin);
  var calls=new ArrayList<String>();
  hooks.add(()->calls.add("cover"));
  hooks.add(()->calls.add("painter"));
  var reel=DiscSurfaceRaster.reel(s->{});
  field(reel,"ready").set(reel,new DiscSurfaceRaster.Raster(){
   public void draw(DrawScope scope,float x,float y,float diameter){}
   public void close(){calls.add("reel");throw new UnsatisfiedLinkError("simulated native release failure");}
  });
  var vinyl=DiscSurfaceRaster.vinyl(s->{});
  field(plugin,"reelRasters").set(plugin,reel);field(plugin,"vinylSurfaceRaster").set(plugin,vinyl);
  plugin.stop();plugin.stop();
  check(calls.equals(List.of("reel","painter","cover")),"continue cleanup in reverse hook order, exactly once");
  check(hooks.isEmpty()&&field(vinyl,"closed").getBoolean(vinyl),"remaining resources closed");
  ((ExecutorService)field(plugin,"logWriter").get(plugin)).submit(()->{}).get(5,TimeUnit.SECONDS);
  String log=Files.readString(dir.resolve("cd-sample.log"));
  check(log.contains("STOP_WARNING stage=reel-raster cause=UnsatisfiedLinkError"),"stage and cause recorded");
 }
 private static void restart(Path dir)throws Exception{
  var events=new ArrayList<String>();
  var registrar=new HookRegistrar(){
   boolean rejectPainter=true;
   HookHandle install(String name){events.add("+"+name);return ()->events.add("-"+name);}
   public HookHandle hookMethod(String owner,String name,List<String> args,MethodHook hook){
    if(name.equals("invokeSuspend"))return install("artwork");
    if(rejectPainter)throw new IllegalStateException("painter unavailable");
    return install("painter");
   }
   public HookHandle replaceComposable(String o,String n,String c,List<String> a,int priority,ComposableHook h){return install("cover");}
   public HookHandle afterComposable(String o,String n,String c,List<String> a,int p,ComposableHook h){throw new AssertionError(n);}
   public <S> HookHandle appendContent(String o,String n,String c,String slot,Class<S> t,List<String> a,int p,ContentHook<S> h){throw new AssertionError(n);}
  };
  Path file=dir.resolve("config.json");
  var managers=new ArrayList<DiscSettingsTest.Manager>();
  var loader=HostLifecycleTest.class.getClassLoader();
  var manager=Proxy.newProxyInstance(loader,new Class[]{WorkshopApi.Manager.class},(p,m,a)->{
   if(m.getName().equals("isPermissionGranted"))return true;
   if(m.getName().equals("createConfigManager")){
    var config=new DiscSettingsTest.Manager(file,DiscSettingsTest.helper(file));managers.add(config);return config;
   }
   throw new AssertionError(m);
  });
  var playback=Proxy.newProxyInstance(loader,new Class[]{WorkshopApi.Playback.class},(p,m,a)->null);
  var previous=WorkshopApi.Companion.instance;
  var plugin=new CdPageProbePlugin(new PluginContext("test","test",dir.toString(),HostCompatibility.current().version(),Channel.MS));
  try{
   WorkshopApi.Companion.setInstance((WorkshopApi)Proxy.newProxyInstance(loader,new Class[]{WorkshopApi.class},(p,m,a)->switch(m.getName()){
    case "getManager" -> manager;case "getHookRegistrar" -> registrar;case "getPlayback" -> playback;default -> throw new AssertionError(m);
   }));
   SwingUtilities.invokeAndWait(()->{
    try{plugin.start();throw new AssertionError("painter registration should fail");}
    catch(IllegalStateException expected){check(expected.getMessage().contains("painter-hook"),"correct rollback stage");}
    check(events.equals(List.of("+artwork","+cover","-cover","-artwork")),"reverse rollback after two successful registrations");
    events.clear();registrar.rejectPainter=false;
    try{plugin.start();plugin.start();}finally{plugin.stop();plugin.stop();}
    check(events.equals(List.of("+artwork","+cover","+painter","-painter","-cover","-artwork")),"one registration per hook on retry");
   });
   check(managers.size()==2&&managers.stream().allMatch(m->m.listeners.isEmpty()&&m.closes==1),"both settings lifetimes released");
  }finally{
   WorkshopApi.Companion.instance=previous;
   ((ExecutorService)field(plugin,"logWriter").get(plugin)).submit(()->{}).get(5,TimeUnit.SECONDS);
  }
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("disc-lifecycle");
  try{
   var failures=new ArrayList<String>();
   try{settings(dir);}catch(AssertionError failure){failures.add(failure.toString());}
   try{stop(dir);}catch(AssertionError|LinkageError failure){failures.add(failure.toString());}
   check(failures.isEmpty(),failures.toString());
   restart(dir);
  }finally{
   try(var paths=Files.walk(dir)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
  }
  System.out.println("PASS partial listener registration rollback, cleanup error preservation, normal re-registration and isolated native disposal");
 }
}
