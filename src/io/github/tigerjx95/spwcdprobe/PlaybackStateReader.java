package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.function.Consumer;
/** Per-host read-only binding. PlaybackService's base class name is not stable. */
final class PlaybackStateReader {
 private Field service;
 private Method player,isPlaying;
 private boolean warned,unsupported;
 private void verifyHost(){HostCompatibility.current().verifyPlayback(getClass().getClassLoader());}
 boolean playing(Consumer<String> report){
  if(unsupported)return false;
  try{
   if(service==null){
    try{verifyHost();}catch(RuntimeException mismatch){unsupported=true;throw mismatch;}
    ClassLoader loader=getClass().getClassLoader();
    Field next=Class.forName("com.xuncorp.voxzen.service.PlaybackController",false,loader).getDeclaredField("service");next.setAccessible(true);
    player=next.getType().getMethod("getPlayer");isPlaying=player.getReturnType().getMethod("ՠ");service=next;
   }
   Object host=service.get(null);return host!=null&&Boolean.TRUE.equals(isPlaying.invoke(player.invoke(host)));
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
   if(!warned){warned=true;report.accept("ROTATION_STATE_UNAVAILABLE "+unavailable);}return false;
  }
 }
}
