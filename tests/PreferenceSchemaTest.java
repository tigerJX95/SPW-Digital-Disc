package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.*;
/** Executes the pinned host's actual native-settings schema parser. */
public class PreferenceSchemaTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var parser=Class.forName(TestHost.parser()).getMethod("\u037f",String.class);
  var groups=(List<?>)parser.invoke(null,Files.readString(Path.of("preference_config.json")));
  check(groups.size()==1,"one native settings group");Object group=groups.get(0);
  var items=(List<?>)group.getClass().getMethod("ԩ").invoke(group);check(items.size()==3,"exposure slider and two independent switches");
  Object slider=items.get(0);var type=slider.getClass();check(type.getName().equals(TestHost.model()+"$Ԫ"),"host recognizes actual seekbar model");
  check(type.getMethod("\u037f").invoke(slider).equals(DiscExposure.KEY),"schema and runtime use same key");
  check(type.getMethod("Ԭ").invoke(slider).equals("config.json"),"schema and runtime use same file");
  check((Float)type.getMethod("ԩ").invoke(slider)==0f&&(Float)type.getMethod("Ԫ").invoke(slider)==100f&&(Float)type.getMethod("ԫ").invoke(slider)==50f,"native slider bounds/default");
  Object toggle=items.get(1);var toggleType=toggle.getClass();
  check(toggleType.getName().equals(TestHost.model()+"$ԫ"),"native switch model");
  check(toggleType.getMethod("\u037f").invoke(toggle).equals(DiscSettings.FRONT_KEY),"matching front key");
  check(!(Boolean)toggleType.getMethod("Ԫ").invoke(toggle),"front mode defaults off");
  check(toggleType.getMethod("ԫ").invoke(toggle).equals("config.json"),"front and exposure share persisted file");
  Object rotation=items.get(2);var rt=rotation.getClass();
  check(rt.getMethod("\u037f").invoke(rotation).equals(DiscSettings.ROTATE_KEY),"rotation key");
  check(!(Boolean)rt.getMethod("Ԫ").invoke(rotation),"rotation defaults off");
  System.out.println("PASS actual SPW preference_config.json parser: seekbar plus front/rotation switches, matching keys/file, 0-100 bounds/default50 and both switches default off");
 }
}
