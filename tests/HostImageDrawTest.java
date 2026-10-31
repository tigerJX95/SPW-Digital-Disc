package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.*;
import androidx.compose.ui.graphics.drawscope.*;
import androidx.compose.ui.node.LayoutNodeDrawScope;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Real host draw-scope implementations; only the final Canvas/Paint are test doubles. */
public class HostImageDrawTest {
 static Object defaultValue(Class<?> type){
  if(type==boolean.class)return false;if(type==int.class)return 0;
  if(type==long.class)return 0L;if(type==float.class)return 0f;
  return null;
 }
 public static void main(String[] args)throws Exception{
  AtomicInteger draws=new AtomicInteger();
  ImageBitmap bitmap=(ImageBitmap)Proxy.newProxyInstance(ImageBitmap.class.getClassLoader(),new Class[]{ImageBitmap.class},(p,m,a)->defaultValue(m.getReturnType()));
  Paint paint=(Paint)Proxy.newProxyInstance(Paint.class.getClassLoader(),new Class[]{Paint.class},(p,m,a)->defaultValue(m.getReturnType()));
  Canvas canvas=(Canvas)Proxy.newProxyInstance(Canvas.class.getClassLoader(),new Class[]{Canvas.class},(p,m,a)->{
   if(!m.getName().equals("drawImageRect-HPBpro0"))throw new AssertionError(m);
   if(a[0]!=bitmap||a[5]!=paint)throw new AssertionError("image/paint identity");
   long dest=(Long)a[3],size=(Long)a[4];
   if((int)(dest>>>32)!=100||(int)dest!=0||(int)(size>>>32)!=400||(int)size!=400)throw new AssertionError("destination bounds");
   draws.incrementAndGet();return null;
  });
  CanvasDrawScope delegate=new CanvasDrawScope();
  delegate.getDrawParams().setCanvas(canvas);
  Field fill=CanvasDrawScope.class.getDeclaredField("fillPaint");fill.setAccessible(true);fill.set(delegate,paint);
  LayoutNodeDrawScope realScope=new LayoutNodeDrawScope(delegate);
  Method draw=LayoutNodeDrawScope.class.getMethod("drawImage-9jGpkUE",ImageBitmap.class,long.class,long.class,long.class,long.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
  try{
   draw.invoke(realScope,bitmap,0L,0L,0L,0L,1f,null,null,3);
   throw new AssertionError("old null style unexpectedly accepted");
  }catch(InvocationTargetException e){if(!(e.getCause() instanceof NullPointerException))throw e;}
  if(draws.get()!=0)throw new AssertionError("invalid draw reached Canvas");
  System.out.println("PASS reproduced reported NullPointerException using real host LayoutNodeDrawScope with old null DrawStyle");
  DiscImage.drawBitmap(realScope,bitmap,1000,1000,300,200,400);
  if(draws.get()!=1)throw new AssertionError("fixed production call did not reach Canvas");
  System.out.println("PASS corrected production DiscImage call through real LayoutNodeDrawScope and CanvasDrawScope to Canvas, with expected image and full destination bounds");
  System.out.println("Final Canvas/Paint are test doubles; Windows native pixel display still awaits real-machine confirmation.");
 }
}
