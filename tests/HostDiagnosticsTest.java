package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.spw.workshop.api.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class HostDiagnosticsTest {
 public static HostDiagnosticsTest service;
 private boolean broken,playing;
 public HostDiagnosticsTest getPlayer(){return this;}
 public boolean isPlaying(){
  if(broken)throw new IllegalStateException("private track path");
  return playing;
 }
 private static Field field(Class<?> type,String name)throws Exception{
  Field result=type.getDeclaredField(name);result.setAccessible(true);return result;
 }
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static <T> T proxy(Class<T> type,InvocationHandler handler){
  return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
 }
 private static void rotation()throws Exception{
  var reader=new PlaybackStateReader();
  var messages=new ArrayList<String>();
  field(PlaybackStateReader.class,"service").set(reader,HostDiagnosticsTest.class.getField("service"));
  field(PlaybackStateReader.class,"player").set(reader,HostDiagnosticsTest.class.getMethod("getPlayer"));
  field(PlaybackStateReader.class,"isPlaying").set(reader,HostDiagnosticsTest.class.getMethod("isPlaying"));
  var fixture=new HostDiagnosticsTest();service=fixture;fixture.broken=true;
  for(int i=0;i<8;i++)check(!reader.playing(messages::add),"failure should freeze rotation");
  service=null;check(!reader.playing(messages::add),"missing service should freeze rotation");
  check(messages.equals(List.of("ROTATION_STATE_UNAVAILABLE stage=read cause=IllegalStateException")),"single diagnostic, no track path or premature recovery");
  service=fixture;fixture.broken=false;
  check(!reader.playing(messages::add),"paused recovery should stay paused");
  fixture.playing=true;check(reader.playing(messages::add),"rotation resumes on play");
  check(messages.size()==2&&messages.get(1).equals("ROTATION_STATE_RECOVERED stage=read"),"single recovery");
  fixture.broken=true;check(!reader.playing(messages::add)&&messages.size()==3,"new outage recorded");
 }
 private static void startup(Path dir,Set<PluginPermission> missing,String expected)throws Exception{
  var manager=proxy(WorkshopApi.Manager.class,(p,m,a)->{
   if(m.getName().equals("isPermissionGranted"))return !missing.contains(a[0]);
   throw new AssertionError(m);
  });
  WorkshopApi.Companion.setInstance(proxy(WorkshopApi.class,(p,m,a)->{
   if(m.getName().equals("getManager"))return manager;
   if(m.getName().equals("getHookRegistrar")&&missing.isEmpty())throw new IllegalStateException("private hook message");
   throw new AssertionError(m);
  }));
  Path log=dir.resolve("cd-sample.log");Files.deleteIfExists(log);
  var context=new PluginContext("test","candidate",dir.resolve("plugin.spmod").toString(),HostCompatibility.current().version(),Channel.Steam);
  var plugin=new CdPageProbePlugin(context);
  try{plugin.start();throw new AssertionError("startup should fail");}
  catch(IllegalStateException error){
   if(!missing.isEmpty())check(error.getMessage().equals("缺少插件权限："+expected),"only missing permissions");
   else check(error.getCause()!=null,"startup retains cause");
  }
  var writer=(ExecutorService)field(CdPageProbePlugin.class,"logWriter").get(plugin);
  writer.submit(()->{}).get(5,TimeUnit.SECONDS);
  String text=Files.readString(log);
  String diagnostic=missing.isEmpty()?"cover-status-hook cause=IllegalStateException":"permissions missing="+expected;
  check(text.contains("HOST_DETECTED plugin=candidate")&&text.contains("START_FAILED stage="+diagnostic),text);
  check(!text.contains("private hook message"),"host exception text should stay out of diagnostics");
  if(missing.isEmpty())check(text.contains("STOP SelfDraw disabled"),"partial startup cleanup");
 }
 public static void main(String[] args)throws Exception{
  rotation();
  var relative=new CdPageProbePlugin(new PluginContext("test","candidate","plugin.spmod","1.19.3",Channel.MS));
  check(field(CdPageProbePlugin.class,"log").get(relative).equals(Path.of("cd-sample.log").toAbsolutePath()),"relative installed package path");
  Path dir=Files.createTempDirectory("spw-host-diagnostic");
  var previous=WorkshopApi.Companion.instance;
  try{
   startup(dir,Set.of(PluginPermission.CLASS_TRANSFORM),"class-transform");
   startup(dir,Set.of(PluginPermission.LIBRARY_READ),"library-read");
   startup(dir,Set.of(PluginPermission.CLASS_TRANSFORM,PluginPermission.LIBRARY_READ),"class-transform, library-read");
   startup(dir,Set.of(),"");
  }finally{
   WorkshopApi.Companion.instance=previous;
   Files.deleteIfExists(dir.resolve("cd-sample.log"));Files.delete(dir);
  }
  System.out.println("PASS startup diagnostics, exact permissions, partial startup cleanup and rotation outage/recovery");
 }
}
