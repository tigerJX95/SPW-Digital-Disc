package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.*;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.Alignment;
import androidx.compose.runtime.Composer;
import androidx.compose.foundation.layout.BoxKt;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;
public class HostEntryTest {
 public abstract static class RenamedSize {
  public abstract long renamed();
  public long concrete(){return 0;}
 }
 public abstract static class AmbiguousSize {
  public abstract long first();
  public abstract long second();
 }
 static void rejectsSize(Class<?> type){
  try{PainterDrawBinding.resolvePackedSize(type);}
  catch(IllegalStateException expected){return;}
  throw new AssertionError("Expected size binding to reject "+type.getName());
 }
 public static class FakePainterElement {
  public androidx.compose.ui.graphics.painter.Painter getPainter(){return null;}
 }
 public static void main(String[] args)throws Exception{
  Class<?> clip=androidx.compose.ui.graphics.ClipOp.class;
  if(!clip.getMethod("access$getDifference$cp").invoke(null).equals(0)||!clip.getMethod("access$getIntersect$cp").invoke(null).equals(1))throw new AssertionError("host clipping operation IDs");
  androidx.compose.ui.graphics.Canvas.class.getMethod("clipPath-mtrdD-E",androidx.compose.ui.graphics.Path.class,int.class);
  androidx.compose.ui.graphics.Path.class.getMethod("addOval",androidx.compose.ui.geometry.Rect.class,androidx.compose.ui.graphics.Path.Direction.class);
  androidx.compose.ui.graphics.SkiaBackedPath_skikoKt.class.getMethod("Path");
  System.out.println("PASS actual host oval Path factory/operation ABI and Intersect=1, Difference=0 for transparent CD hole");
  Class<?> resolverClass=Class.forName("com.xuncorp.voxzen.workshop.hooks.Ԫ");Object resolver=resolverClass.getField("\u037f").get(null);
  var ui=resolverClass.getMethod("\u037f",String.class,String.class,String.class,List.class);
  var normal=resolverClass.getMethod("\u037f",String.class,String.class,List.class);
  System.out.println("PASS "+ui.invoke(resolver,HostCompatibility.current().owner(),"\u037f",HostCompatibility.current().caller(),List.of("kotlin.jvm.functions.Function0","boolean","boolean","boolean","androidx.compose.ui.Modifier")));
  var draw=PainterDrawBinding.METHOD;
  if(!PainterDrawBinding.INTRINSIC_SIZE.getName().equals("getIntrinsicSize-NH-jbRc"))
   throw new AssertionError("intrinsic binding must match the actual host size method");
  if(!PainterDrawBinding.resolvePackedSize(RenamedSize.class).getName().equals("renamed"))
   throw new AssertionError("renamed intrinsic size method");
  if(!PainterDrawBinding.LAYOUT_SIZE.getName().equals("getSize-YbymL2g"))
   throw new AssertionError("layout size binding must match the actual host method");
  rejectsSize(AmbiguousSize.class);
  rejectsSize(Object.class);
  var cacheField=HostCoverBridge.class.getDeclaredField("PAINTER_GETTERS");
  cacheField.setAccessible(true);
  var cache=(ClassValue<?>)cacheField.get(null);
  var painterElement=Class.forName("androidx.compose.ui.draw.PainterElement");
  if(((Optional<?>)cache.get(painterElement)).isEmpty()||((Optional<?>)cache.get(FakePainterElement.class)).isPresent())
   throw new AssertionError("only the actual Compose PainterElement supplies artwork");
  System.out.println("PASS "+normal.invoke(resolver,draw.getDeclaringClass().getName(),draw.getName(),PainterDrawBinding.PARAMETERS));
  var method=BoxKt.class.getMethod("Box",Modifier.class,Alignment.class,boolean.class,Function3.class,Composer.class,int.class,int.class);
  if(method==null)throw new AssertionError();
  if(!"library-read".equals(com.xuncorp.spw.workshop.api.PluginPermission.LIBRARY_READ.getId()))throw new AssertionError("permission ID differs");
  System.out.println("PASS real library-read permission identifier");
  Object permissions=Class.forName(TestHost.newer()?"com.xuncorp.voxzen.workshop.permissions.Ϳ":"com.xuncorp.voxzen.workshop.ޅ").getMethod("\u037f",String.class).invoke(null,"class-transform,library-read");
  if(!permissions.equals(Set.of("class-transform","library-read")))throw new AssertionError("manifest permissions parsing");
  System.out.println("PASS actual host parses both declared manifest permissions");
  Modifier widget=CanvasWidget.modifier(SelfDrawLayout.fit(1673,930,1,false),1,()->Unit.INSTANCE);
  if(widget==null)throw new AssertionError();
  System.out.println("PASS real Box ABI and real independently-sized offset/clickable Modifier construction");
 }
}
