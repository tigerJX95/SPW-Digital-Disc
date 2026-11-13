package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.*;
public class RotationSettingsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public abstract static class RenamedPlayer {
  public abstract boolean renamed();
  public boolean concrete(){return false;}
 }
 public abstract static class AmbiguousPlayer {
  public abstract boolean first();
  public abstract boolean second();
 }
 static void rejects(Class<?> type)throws Exception{
  try{PlaybackStateReader.resolvePlaying(type);}
  catch(NoSuchMethodException expected){return;}
  throw new AssertionError("Expected playback binding to reject "+type.getName());
 }
 static javax.swing.AbstractButton button(java.awt.Container parent,String text){
  for(var child:parent.getComponents()){
   if(child instanceof javax.swing.AbstractButton option&&text.equals(option.getText()))return option;
   if(child instanceof java.awt.Container container){
    var match=button(container,text);
    if(match!=null)return match;
   }
  }
  return null;
 }
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempDirectory("rotation-settings").resolve("config.json");
  var helper=DiscSettingsTest.helper(file);var manager=new DiscSettingsTest.Manager(helper);
  var values=new ArrayList<DiscSettings.Values>();
  try(var settings=new DiscSettings(manager,values::add)){
   check(!values.getLast().rotate(),"old profile rotation off");
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE)),"fresh profile defaults to CD with ocean blue available");
   helper.set(DiscSettings.ROTATE_KEY,true);manager.fire();DiscSettingsTest.flush();
   check(values.getLast().equals(new DiscSettings.Values(50,false,true,false)),"rotation independent of amount and front");
   helper.set(DiscSettings.FRONT_KEY,true);helper.set(DiscExposure.KEY,100f);manager.fire();DiscSettingsTest.flush();
   check(values.getLast().equals(new DiscSettings.Values(100,true,true,false)),"three fields published together");
   helper.set(DiscSettings.VINYL_KEY,true);manager.fire();DiscSettingsTest.flush();
   check(values.getLast().equals(new DiscSettings.Values(100,true,true,true)),"vinyl setting independent of other options");
   helper.set(DiscSettings.MOTION_SPEED_KEY,"FAST");helper.set(DiscSettings.ROTATION_SPEED_KEY,"SLOW");
   manager.fire();DiscSettingsTest.flush();
   check(values.getLast().equals(new DiscSettings.Values(100,true,true,true,DiscSettings.Speed.FAST,DiscSettings.Speed.SLOW)),"independent speed selections");
   helper.set(DiscSettings.VINYL_COLOR_KEY,"BLUE");manager.fire();DiscSettingsTest.flush();
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.COLORED_VINYL,DiscSettings.VinylColor.BLUE)),"legacy blue vinyl migrates");
   check(settings.saveAppearance(new DiscSettings.Appearance(DiscSettings.Style.BLACK_VINYL,DiscSettings.VinylColor.PINK)),"save style and remembered color");
   DiscSettingsTest.flush();
   check(values.getLast().vinylColor()==DiscSettings.VinylColor.BLACK,"classic vinyl always renders black");
   check(helper.save(),"save using host storage");
  }
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(DiscSettingsTest.helper(file)),values::add)){
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.BLACK_VINYL,DiscSettings.VinylColor.PINK))
     &&values.getLast().motionSpeed()==DiscSettings.Speed.FAST&&values.getLast().rotationSpeed()==DiscSettings.Speed.SLOW,"restart restores color, both speeds and vinyl");
  }
  var invalid=DiscSettingsTest.helper(file);
  invalid.set(DiscSettings.STYLE_KEY,"bad");
  invalid.set(DiscSettings.VINYL_COLOR_KEY,"INVALID");
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(invalid),values::add)){
   check(values.getLast().vinyl()&&values.getLast().vinylColor()==DiscSettings.VinylColor.BLACK,"invalid color preserves black vinyl fallback");
  }
  invalid.set(DiscSettings.VINYL_KEY,false);
  invalid.set(DiscSettings.VINYL_COLOR_KEY,"BLUE");
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(invalid),values::add)){
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE)),"disabled legacy vinyl stays CD and remembers color");
  }
  javax.swing.SwingUtilities.invokeAndWait(()->{
   var saved=new java.util.concurrent.atomic.AtomicReference<DiscSettings.Appearance>();
   var panel=DiscAppearanceDialog.content(new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE),value->{saved.set(value);return true;},()->{});
   var blue=button(panel,"海洋蓝");
   var red=button(panel,"玫瑰红");
   var section=blue.getParent().getParent();
   check(!section.isVisible()&&!blue.isEnabled()&&blue.getIcon()!=null&&blue.isSelected(),"color section hidden on CD, ocean blue preselected");
   for(var style:DiscSettings.Style.values()){
    var option=button(panel,style.label);
    var insets=option.getInsets();
    int textWidth=option.getFontMetrics(option.getFont()).stringWidth(style.label);
    check(option.getPreferredSize().width>=textWidth+insets.left+insets.right+12,"full style label fits: "+style.label);
   }
   int width=panel.getPreferredSize().width;
   button(panel,"彩胶唱片").doClick();
   check(section.isVisible()&&blue.isEnabled(),"selecting colored vinyl reveals color swatches");
   check(panel.getPreferredSize().width==width,"color swatches keep the dialog width stable");
   red.doClick();
   button(panel,"经典黑胶唱片").doClick();
   check(!section.isVisible()&&!blue.isEnabled(),"classic vinyl hides color swatches");
   button(panel,"彩胶唱片").doClick();
   check(red.isSelected()&&saved.get()==null,"draft color retained across style switches");
   button(panel,"确认").doClick();
   check(saved.get().equals(new DiscSettings.Appearance(DiscSettings.Style.COLORED_VINYL,DiscSettings.VinylColor.RED)),"confirmation saves the selected appearance");
  });
  var reader=new PlaybackStateReader();
  var verify=PlaybackStateReader.class.getDeclaredMethod("verifyHost");verify.setAccessible(true);verify.invoke(reader);
  // Resolve the exact pinned read-only ABI without initializing its native audio engine.
  var controller=Class.forName("com.xuncorp.voxzen.service.PlaybackController",false,RotationSettingsTest.class.getClassLoader());
  var field=controller.getDeclaredField("service");check(java.lang.reflect.Modifier.isStatic(field.getModifiers()),"host service static");
  check(field.getType().getMethod("getPlayer").getReturnType()==com.xuncorp.pisces.PiscesPlayer.class,"player getter ABI");
  var playerType=field.getType().getMethod("getPlayer").getReturnType();
  check(PlaybackStateReader.resolvePlaying(playerType).equals(playerType.getMethod("ՠ")),"matches known host isPlaying ABI");
  check(PlaybackStateReader.resolvePlaying(RenamedPlayer.class).getName().equals("renamed"),"accepts renamed abstract getter");
  rejects(AmbiguousPlayer.class);
  rejects(Object.class);
  System.out.println("PASS rotation default, independent/atomic settings, actual host save/restart and pinned playback reader ABI (native audio not run)");
 }
}
