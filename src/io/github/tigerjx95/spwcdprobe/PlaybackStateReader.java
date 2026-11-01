package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.Field;
import java.util.function.Consumer;
/** Read-only adapter for the pinned 1.19.0 MS host. Public Workshop Playback has no isPlaying getter. */
final class PlaybackStateReader {
 private Field service;
 private boolean warned,unsupported;
 private void verifyHost()throws ReflectiveOperationException {
  try{
  verify("com.xuncorp.voxzen.service.PlaybackController","9d6515f6886ca7fdd74d9dbe3f80294e5e537e5c92ae9457a134cc4d6aba6907");
  verify("androidx.compose.ui.ݱ","c5d71ffba956640547e0a63bf9287ef1b491c978df0c0f1e1c5759cadeb11b36");
  verify("com.xuncorp.pisces.PiscesPlayer","66cacfe8b14d395263c8747a6d579af3441b857ca80e396181200c3ae15635f8");
  }catch(Exception error){unsupported=true;throw new ReflectiveOperationException("Pinned playback ABI fingerprint mismatch",error);}
 }
 private static void verify(String name,String expected)throws Exception {
  try(var input=PlaybackStateReader.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){
   if(input==null||!expected.equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))))throw new IllegalStateException(name);
  }
 }
 boolean playing(Consumer<String> report){
  if(unsupported)return false;
  try{
   if(service==null){verifyHost();}
   if(service==null){service=com.xuncorp.voxzen.service.PlaybackController.class.getDeclaredField("service");service.setAccessible(true);}
   var host=(com.xuncorp.voxzen.service.PlaybackService)service.get(null);
   // The same player getter used by PlaybackService.lyricsEmitter$lambda$2.
   return host!=null&&host.getPlayer().ՠ();
  }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
   if(!warned){warned=true;report.accept("ROTATION_STATE_UNAVAILABLE "+unavailable);}
   return false;
  }
 }
}
