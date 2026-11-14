package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.*;
/** Executes the pinned host's actual native-settings schema parser. */
public class PreferenceSchemaTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var parser=Class.forName(TestHost.parser()).getMethod("\u037f",String.class);
  var groups=(List<?>)parser.invoke(null,Files.readString(Path.of("plugin/preference_config.json")));
  check(groups.size()==1,"one native settings group");Object group=groups.get(0);
  var items=(List<?>)group.getClass().getMethod("ԩ").invoke(group);check(items.size()==8,"exposure slider, two switches, two buttons and three lists");
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
  Object appearance=items.get(3);var appearanceType=appearance.getClass();
  check(appearanceType.getName().equals(TestHost.model()+"$Ϳ"),"native appearance button model");
  check(appearanceType.getMethod("Ԫ").invoke(appearance).equals(DiscAppearanceDialog.class.getName()+".open"),"appearance callback");
  DiscAppearanceDialog.open();
  for(int i=4;i<6;i++){
   Object speed=items.get(i);var list=speed.getClass();
   check(list.getName().equals(TestHost.model()+"$ԩ"),"native list model");
   check(list.getMethod("Ϳ").invoke(speed).equals(i==4?DiscSettings.MOTION_SPEED_KEY:DiscSettings.ROTATION_SPEED_KEY),"speed key");
   check(list.getMethod("Ԫ").invoke(speed).equals(List.of("慢速","标准","快速")),"speed labels");
   check(list.getMethod("ԫ").invoke(speed).equals(List.of("SLOW","STANDARD","FAST")),"speed values");
   check(list.getMethod("Ԭ").invoke(speed).equals("STANDARD"),"standard default");
  }
  Object order=items.get(6);var button=order.getClass();
  check(button.getName().equals(TestHost.model()+"$Ϳ"),"native button model");
  String action=(String)button.getMethod("Ԫ").invoke(order);
  check(action.equals(DiscOrderDialog.class.getName()+".open"),"button targets the plugin callback");
  var method=DiscOrderDialog.class.getMethod("open");
  check(java.lang.reflect.Modifier.isStatic(method.getModifiers()),"callback is public and static");
  method.invoke(null); // Host may open settings even while the plugin is stopped.
  Object trim=items.get(7);var trimType=trim.getClass();
  check(trimType.getMethod("Ϳ").invoke(trim).equals(DiscSettings.TRIM_COVER_KEY),"trim mode key");
  check(trimType.getMethod("ԫ").invoke(trim).equals(List.of("OFF","PAIRED","RIGHT")),"mutually exclusive trim modes");
  check(trimType.getMethod("Ԭ").invoke(trim).equals("OFF"),"trim defaults off");
  var file=Files.createTempFile("spw-trim-test",".json");
  try{
   Files.writeString(file,"{}");
   var config=DiscSettingsTest.helper(file);
   check(DiscSettings.trimCover(config)==CoverWhiteBorders.Mode.OFF,"fresh default");
   config.set(DiscSettings.LEGACY_TRIM_COVER_KEY,true);
   check(DiscSettings.trimCover(config)==CoverWhiteBorders.Mode.PAIRED,"legacy enabled becomes paired");
   config.set(DiscSettings.TRIM_COVER_KEY,"OFF");
   check(DiscSettings.trimCover(config)==CoverWhiteBorders.Mode.OFF,"explicit off overrides legacy");
  }finally{Files.deleteIfExists(file);}
  var right=CoverWhiteBorders.Mode.RIGHT;
  check(CoverWhiteBorders.find(100,100,(x,y)->x<10||x>=76?-1:0xff224466,right)
      .equals(new CoverWhiteBorders.Bounds(0,0,76,100)),"right-only keeps left side");
  check(!CoverWhiteBorders.find(100,100,(x,y)->x>=76?-1:0xff224466,right)
      .trimmedFrom(100,100),"right-only leaves artwork with a white background on just the right intact");
  check(!CoverWhiteBorders.find(100,100,(x,y)->x>=76||x<10&&y!=50?-1:0xff224466,right)
      .trimmedFrom(100,100),"right-only requires an uninterrupted left margin too");
  var fringe=CoverWhiteBorders.find(400,200,(x,y)->{
   if(x<80||x>=300)return -1;
   if(x>=297)return y==50?0xff444444:0xffe8e8e8;
   if(x==296&&y<195)return 0xffeeeeee;
   return 0xff224466;
  },right);
  check(fringe.equals(new CoverWhiteBorders.Bounds(0,0,297,200)),
      "right-only removes a near-white fringe but stops at cover detail");
  check(!CoverWhiteBorders.find(400,200,(x,y)->x>=398?0xffe8e8e8:0xff224466,right)
      .trimmedFrom(400,200),"fringe cleanup requires a confirmed white margin");
  check(!CoverWhiteBorders.find(100,100,(x,y)->x<10||x>=76&&y!=50?-1:0xff224466,right).trimmedFrom(100,100),
      "right-only rejects interrupted white strip");

  check(CoverWhiteBorders.find(100,100,(x,y)->x<24||x>=76?-1:0xff224466)
      .equals(new CoverWhiteBorders.Bounds(24,0,52,100)),"vertical white strips");
  check(CoverWhiteBorders.find(100,100,(x,y)->y<18||y>=82?-1:0xff224466)
      .equals(new CoverWhiteBorders.Bounds(0,18,100,64)),"horizontal white strips");
  check(!CoverWhiteBorders.find(100,100,(x,y)->{
   if(y==83&&x==40)return 0xff982b20;
   return y<18||y>=82?-1:0xff224466;
  }).trimmedFrom(100,100),"horizontal trimming preserves both margins when bottom has title detail");
  check(!CoverWhiteBorders.find(100,100,(x,y)->-1).trimmedFrom(100,100),"all-white artwork stays intact");
  check(!CoverWhiteBorders.find(100,100,(x,y)->x<24?-1:0xff224466).trimmedFrom(100,100),"one-sided white stays intact");
  check(new CoverWhiteBorders.Bounds(3,0,94,100).displayAspect()==1f,"thin border fills square");
  check(new CoverWhiteBorders.Bounds(24,0,52,100).displayAspect()==.52f,"thick border preserves portrait");
  check(new CoverWhiteBorders.Bounds(0,20,100,60).displayAspect()>1.6f,"thick border preserves landscape");
  check(CoverWhiteBorders.find(100,100,(x,y)->x<19||x>=81?(y==50?0xff444444:0xffeeeeee):0xff224466)
      .equals(new CoverWhiteBorders.Bounds(19,0,62,100)),"near-white edge tolerates isolated noise");
  System.out.println("PASS actual SPW preference_config.json parser: seekbar, two switches, two buttons and three lists, matching keys/file, 0-100 bounds/default50 and all switches default off");
 }
}
