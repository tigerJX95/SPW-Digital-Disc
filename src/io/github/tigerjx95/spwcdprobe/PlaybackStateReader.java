package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.function.Consumer;
/** Per-host read-only binding. PlaybackService's base class name is not stable. */
final class PlaybackStateReader {
 private Field service;
 private Method player,isPlaying;
 private boolean readFailed,unsupported;
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
 boolean prepare(Consumer<String> report){
  if(unsupported)return false;
  if(service!=null)return true;
  try{
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
   return true;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
   report.accept("ROTATION_STATE_UNAVAILABLE stage=bind cause="+unavailable.getClass().getSimpleName());return false;
  }
 }
 boolean playing(Consumer<String> report){
  if(!prepare(report))return false;
  try{
   Object host=service.get(null);
   if(host==null)return false;
   Object activePlayer=player.invoke(host);
   if(activePlayer==null)return false;
   boolean playing=Boolean.TRUE.equals(isPlaying.invoke(activePlayer));
   if(readFailed){readFailed=false;report.accept("ROTATION_STATE_RECOVERED stage=read");}
   return playing;
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
   if(!readFailed){
    readFailed=true;
    Throwable cause=unavailable instanceof InvocationTargetException invocation&&invocation.getCause()!=null?invocation.getCause():unavailable;
    report.accept("ROTATION_STATE_UNAVAILABLE stage=read cause="+cause.getClass().getSimpleName());
   }
   return false;
  }
 }
}
