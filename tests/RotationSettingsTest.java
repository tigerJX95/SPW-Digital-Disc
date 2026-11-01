package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.*;
public class RotationSettingsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempDirectory("rotation-settings").resolve("config.json");
  var helper=DiscSettingsTest.helper(file);var manager=new DiscSettingsTest.Manager(helper);
  var values=new ArrayList<DiscSettings.Values>();
  try(var settings=new DiscSettings(manager,values::add)){
   check(!values.getLast().rotate(),"old profile rotation off");
   helper.set(DiscSettings.ROTATE_KEY,true);manager.fire();DiscSettingsTest.flush();
   check(values.getLast().equals(new DiscSettings.Values(50,false,true)),"rotation independent of amount and front");
   helper.set(DiscSettings.FRONT_KEY,true);helper.set(DiscExposure.KEY,100f);manager.fire();DiscSettingsTest.flush();
   check(values.getLast().equals(new DiscSettings.Values(100,true,true)),"three fields published together");
   check(helper.save(),"save using host storage");
  }
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(DiscSettingsTest.helper(file)),values::add)){
   check(values.getLast().equals(new DiscSettings.Values(100,true,true)),"restart restores rotation and other fields");
  }
  var reader=new PlaybackStateReader();
  var verify=PlaybackStateReader.class.getDeclaredMethod("verifyHost");verify.setAccessible(true);verify.invoke(reader);
  // Resolve the exact pinned read-only ABI without initializing its native audio engine.
  var controller=Class.forName("com.xuncorp.voxzen.service.PlaybackController",false,RotationSettingsTest.class.getClassLoader());
  var field=controller.getDeclaredField("service");check(java.lang.reflect.Modifier.isStatic(field.getModifiers()),"host service static");
  check(field.getType().getMethod("getPlayer").getReturnType()==com.xuncorp.pisces.PiscesPlayer.class,"player getter ABI");
  check(com.xuncorp.pisces.PiscesPlayer.class.getMethod("ՠ").getReturnType()==boolean.class,"actual isPlaying ABI");
  System.out.println("PASS rotation default, independent/atomic settings, actual host save/restart and pinned playback reader ABI (native audio not run)");
 }
}
