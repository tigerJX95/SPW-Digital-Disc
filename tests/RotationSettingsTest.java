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
  var helper=DiscSettingsTest.helper(file);var manager=new DiscSettingsTest.Manager(file,helper);
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
   check(values.getLast().equals(new DiscSettings.Values(100,true,true,true,DiscSpeed.FAST,DiscSpeed.SLOW)),"independent speed selections");
   helper.set(DiscSettings.VINYL_COLOR_KEY,"BLUE");manager.fire();DiscSettingsTest.flush();
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.COLORED_VINYL,DiscSettings.VinylColor.BLUE)),"legacy blue vinyl migrates");
   check(settings.saveAppearance(new DiscSettings.Appearance(DiscSettings.Style.BLACK_VINYL,DiscSettings.VinylColor.PINK)),"save style and remembered color");
   DiscSettingsTest.flush();
   check(values.getLast().vinylColor()==DiscSettings.VinylColor.BLACK,"classic vinyl always renders black");
   check(helper.save(),"save using host storage");
  }
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(file,DiscSettingsTest.helper(file)),values::add)){
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.BLACK_VINYL,DiscSettings.VinylColor.PINK))
     &&values.getLast().motionSpeed()==DiscSpeed.FAST&&values.getLast().rotationSpeed()==DiscSpeed.SLOW,"restart restores color, both speeds and vinyl");
  }
  Files.delete(file.resolveSibling(DiscSettings.APPEARANCE_CONFIG));
  var invalid=DiscSettingsTest.helper(file);
  invalid.set(DiscSettings.STYLE_KEY,"bad");
  invalid.set(DiscSettings.VINYL_COLOR_KEY,"INVALID");
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(file,invalid),values::add)){
   check(values.getLast().vinyl()&&values.getLast().vinylColor()==DiscSettings.VinylColor.BLACK,"invalid color preserves black vinyl fallback");
   check(DiscSettingsTest.helper(file.resolveSibling(DiscSettings.APPEARANCE_CONFIG))
       .get(DiscSettings.STYLE_KEY,"").equals("BLACK_VINYL:BLUE"),"legacy vinyl is migrated to separate storage");
  }
  Files.delete(file.resolveSibling(DiscSettings.APPEARANCE_CONFIG));
  invalid.set(DiscSettings.VINYL_KEY,false);
  invalid.set(DiscSettings.VINYL_COLOR_KEY,"BLUE");
  try(var settings=new DiscSettings(new DiscSettingsTest.Manager(file,invalid),values::add)){
   check(settings.appearance().equals(new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE)),"disabled legacy vinyl stays CD and remembers color");
  }
  var random=DiscSettings.VinylColor.RANDOM;
  var selected=random.resolve("Album","Album Artist","Singer A",2020,"/music/a.flac",42);
  check(selected!=random&&selected!=DiscSettings.VinylColor.BLACK,"random selects a preset");
  check(selected==random.resolve("Album","Album Artist","Singer B",2020,"/music/b.flac",42),
      "one album keeps its color across tracks and performers");
  javax.swing.SwingUtilities.invokeAndWait(()->{
   var saved=new java.util.concurrent.atomic.AtomicReference<DiscSettings.Appearance>();
   var panel=DiscAppearanceDialog.content(new DiscSettings.Appearance(DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE),value->{saved.set(value);return true;},()->{});
   var blue=button(panel,"海洋蓝");
   var red=button(panel,"玫瑰红");
   var randomButton=button(panel,"随机色");
   check(randomButton.getIcon()!=null&&randomButton.getIcon().getIconWidth()==blue.getIcon().getIconWidth(),"random swatch matches preset size");
   var section=blue.getParent().getParent();
   check(!section.isVisible()&&!blue.isEnabled()&&blue.getIcon()!=null&&blue.isSelected(),"color section hidden on CD, ocean blue preselected");
   for(var style:DiscSettings.Style.values()){
    if(style==DiscSettings.Style.REEL_COVER)continue;
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
   for(var reelColor:DiscSettings.ReelColor.values()){
    var appearance=new DiscSettings.Appearance(DiscSettings.Style.REEL,DiscSettings.VinylColor.BLUE,reelColor);
    check(appearance.displayName().equals(reelColor.label+"开盘磁带"),"album source names the open-reel tape style and color");
   }
   button(panel,"开盘磁带").doClick();
   var artwork=button(panel,"专辑封面");
   check(artwork.getParent().isVisible()&&!section.isVisible(),"reel shows its own appearance choices");
   var silver=button(panel,"拉丝银");
   var reelPalette=silver.getParent();
   check(reelPalette.isVisible()&&silver.isSelected()&&reelPalette.getComponentCount()==7,
       "solid reel shows six presets and random with silver selected by default");
   var reelLabels=java.util.Arrays.stream(reelPalette.getComponents())
       .map(component->((javax.swing.JToggleButton)component).getText()).toList();
   check(reelLabels.equals(java.util.List.of("拉丝银","香槟金","活力橙","象牙白","石墨灰","深酒红","随机色")),
       "reel palette starts with silver and gold and ends with random");
   button(panel,"深酒红").doClick();
   artwork.doClick();
   check(!reelPalette.isVisible(),"album-cover reel hides solid color controls");
   button(panel,"纯色").doClick();
   check(reelPalette.isVisible()&&button(panel,"深酒红").isSelected(),"return to solid retains chosen color");
   button(panel,"确认").doClick();
   check(saved.get().style()==DiscSettings.Style.REEL&&saved.get().reelColor()==DiscSettings.ReelColor.WINE,
       "confirmation saves the selected reel color");
   artwork.doClick();
   button(panel,"确认").doClick();
   check(saved.get().style()==DiscSettings.Style.REEL_COVER,"reel artwork choice reaches settings");
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
