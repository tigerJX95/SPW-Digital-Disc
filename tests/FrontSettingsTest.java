package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
public class FrontSettingsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempDirectory("front-settings").resolve("config.json");
  var helper=DiscSettingsTest.helper(file);var manager=new DiscSettingsTest.Manager(helper);
  var values=new ArrayList<DiscSettings.Values>();
  try(DiscSettings settings=new DiscSettings(manager,values::add)){
   check(values.equals(List.of(DiscSettings.DEFAULT)),"existing profile stays default-back without a front key");
   SwingUtilities.invokeAndWait(()->{
    helper.set(DiscExposure.KEY,25f);helper.set(DiscSettings.FRONT_KEY,true);manager.fire();
   });DiscSettingsTest.flush();
   check(values.get(values.size()-1).equals(new DiscSettings.Values(25,true,false)),"amount and draw order publish as one settings value");
   helper.set(DiscExposure.KEY,50.062717f);manager.fire();DiscSettingsTest.flush();
   check(values.get(values.size()-1).equals(new DiscSettings.Values(50,true,false)),"native slider float rounds independently of front mode");
   check(helper.save(),"save both fields through actual host helper");
   helper.set(DiscSettings.FRONT_KEY,false);manager.fire();DiscSettingsTest.flush();
   check(values.get(values.size()-1).equals(new DiscSettings.Values(50,false,false)),"front toggle does not change exposure");
  }
  var restored=new DiscSettingsTest.Manager(DiscSettingsTest.helper(file));var restart=new ArrayList<DiscSettings.Values>();
  try(DiscSettings settings=new DiscSettings(restored,restart::add)){
   check(restart.equals(List.of(new DiscSettings.Values(50,true,false))),"saved amount and front mode restored together");
  }
  System.out.println("PASS actual host settings storage: old-profile front default off, independent 25/50 amounts, float rounding, atomic settings publication, toggle without movement change and saved front-mode restart");
 }
}
