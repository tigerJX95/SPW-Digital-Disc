package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.function.Consumer;
/** Per-host read-only binding. PlaybackService's base class name is not stable. */
final class PlaybackStateReader {
 private Field service;
 private Method player,isPlaying;
 private boolean warned,unsupported;
 static Method resolvePlaying(Class<?> playerType)throws NoSuchMethodException{
  Method match=null;
  for(Method method:playerType.getDeclaredMethods()){
   int flags=method.getModifiers();
   if(!Modifier.isPublic(flags)||!Modifier.isAbstract(flags)||method.isSynthetic()
     ||method.getReturnType()!=boolean.class||method.getParameterCount()!=0)continue;
   if(match!=null)throw new NoSuchMethodException("Ambiguous playback state getter: "+playerType.getName());
   match=method;
  }
  if(match==null)throw new NoSuchMethodException("Missing playback state getter: "+playerType.getName());
  return match;
 }
 private void verifyHost(){HostCompatibility.current().verifyPlayback(getClass().getClassLoader());}
 boolean playing(Consumer<String> report){
  if(unsupported)return false;
  try{
   if(service==null){
    // A structural mismatch is stable for this plugin class loader.
    unsupported=true;
    verifyHost();
    ClassLoader loader=getClass().getClassLoader();
    Field next=Class.forName("com.xuncorp.voxzen.service.PlaybackController",false,loader).getDeclaredField("service");next.setAccessible(true);
    if(!Modifier.isStatic(next.getModifiers()))throw new NoSuchFieldException("Playback service is not static");
    player=next.getType().getMethod("getPlayer");
    isPlaying=resolvePlaying(player.getReturnType());
    service=next;
    unsupported=false;
   }
   Object host=service.get(null);return host!=null&&Boolean.TRUE.equals(isPlaying.invoke(player.invoke(host)));
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
   if(!warned){warned=true;report.accept("ROTATION_STATE_UNAVAILABLE "+unavailable.getClass().getSimpleName());}return false;
  }
 }
}
