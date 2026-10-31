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
  System.out.println("PASS "+ui.invoke(resolver,"androidx.compose.ui.א","\u037f","androidx.compose.ui.ח",List.of("kotlin.jvm.functions.Function0","boolean","boolean","boolean","androidx.compose.ui.Modifier")));
  System.out.println("PASS "+normal.invoke(resolver,"androidx.compose.ui.graphics.painter.Painter","draw-x_KDEd0",List.of("androidx.compose.ui.graphics.drawscope.DrawScope","long","float","androidx.compose.ui.graphics.ColorFilter")));
  var method=BoxKt.class.getMethod("Box",Modifier.class,Alignment.class,boolean.class,Function3.class,Composer.class,int.class,int.class);
  if(method==null)throw new AssertionError();
  if(!"library-read".equals(com.xuncorp.spw.workshop.api.PluginPermission.LIBRARY_READ.getId()))throw new AssertionError("permission ID differs");
  System.out.println("PASS real library-read permission identifier");
  Object permissions=Class.forName("com.xuncorp.voxzen.workshop.ޅ").getMethod("\u037f",String.class).invoke(null,"class-transform,library-read");
  if(!permissions.equals(Set.of("class-transform","library-read")))throw new AssertionError("manifest permissions parsing");
  System.out.println("PASS actual host parses both declared manifest permissions");
  Modifier widget=CanvasWidget.modifier(SelfDrawLayout.fit(1673,930,1,false),1,()->Unit.INSTANCE);
  if(widget==null)throw new AssertionError();
  System.out.println("PASS real Box ABI and real independently-sized offset/clickable Modifier construction");
 }
}
