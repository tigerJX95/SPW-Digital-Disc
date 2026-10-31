package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.Canvas;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.ColorKt;
import androidx.compose.ui.graphics.Path;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.geometry.Rect;
import java.util.function.Supplier;
import androidx.compose.ui.graphics.drawscope.*;
import androidx.compose.ui.graphics.painter.Painter;
import java.lang.reflect.Method;
/** Draws into our own full-width Canvas. Never invoked inside the native Image node. */
final class RootRenderer {
 interface Source {void draw(DrawScope scope,float width,float height)throws Exception;}
 interface Disc {boolean draw(DrawScope scope,float cx,float cy,float diameter)throws Exception;}
 private static final Method CLIP,CLIP_RECT,CIRCLE,SIZE,INTRINSIC,DRAW;
 static {
  try{
   CLIP_RECT=Canvas.class.getMethod("clipRect-N_I0leg",float.class,float.class,float.class,float.class,int.class);
   CLIP=Canvas.class.getMethod("clipPath-mtrdD-E",Path.class,int.class);
   CIRCLE=DrawScope.class.getMethod("drawCircle-VaOC9Bg",long.class,float.class,long.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
   SIZE=DrawScope.class.getMethod("getSize-NH-jbRc");
   INTRINSIC=Painter.class.getMethod("getIntrinsicSize-NH-jbRc");
   DRAW=Painter.class.getMethod("draw-x_KDEd0",DrawScope.class,long.class,float.class,ColorFilter.class);
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc)throws Exception{
  draw(scope,painter,enter,leave,disc,1f);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure)throws Exception{
  long intrinsic=(Long)INTRINSIC.invoke(painter);float iw=x(intrinsic),ih=y(intrinsic);
  if(!Float.isFinite(iw)||!Float.isFinite(ih)||iw<=0||ih<=0){iw=ih=1;}
  final float ratio=iw/ih;
  paint(scope,ratio,(s,w,h)->{
   enter.run();try{DRAW.invoke(painter,s,pack(w,h),1f,null);}finally{leave.run();}
  },disc,SkiaBackedPath_skikoKt::Path,exposure);
 }
 static void paint(DrawScope scope,float aspect,Source source)throws Exception{paint(scope,aspect,source,(s,x,y,d)->false);}
 static void paint(DrawScope scope,float aspect,Source source,Disc disc)throws Exception{
  paint(scope,aspect,source,disc,SkiaBackedPath_skikoKt::Path);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths)throws Exception{
  paint(scope,aspect,source,disc,paths,1f);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure)throws Exception{
  exposure=Math.max(0,Math.min(1,exposure));
  long size=(Long)SIZE.invoke(scope);float w=x(size),h=y(size),side=Math.min(h,w/1.5f);
  if(side<=0||!Float.isFinite(side))return;
  // All disc pixels, including its shadow, lie inside width=1.5*side.
  float cx=side*(.5f+.5f*exposure),cy=side*.5f,r=side*.49f;
  Canvas moving=scope.getDrawContext().getCanvas();moving.save();
  try{
   CLIP_RECT.invoke(moving,side,0f,w,side,1); // Only pixels emerging from the sleeve edge.
   if(exposure>0){
  if(!disc.draw(scope,cx,cy,r*2f)){
   Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
   try{
    Path outer=paths.get();outer.addOval(new Rect(cx-r,cy-r,cx+r,cy+r),Path.Direction.Clockwise);
    Path hole=paths.get();float hr=r*.125f;hole.addOval(new Rect(cx-hr,cy-hr,cx+hr,cy+hr),Path.Direction.Clockwise);
    CLIP.invoke(canvas,outer,1); // Intersect with outer circle.
    CLIP.invoke(canvas,hole,0);  // Difference: genuine transparent center hole.
    // Fill the circle without stretching; crop only this secondary copy.
    float dw=aspect>=1?r*2*aspect:r*2,dh=aspect>=1?r*2:r*2/aspect;
    canvas.save();
    try{canvas.translate(cx-dw*.5f,cy-dh*.5f);source.draw(scope,dw,dh);}finally{canvas.restore();}
    // Translucent hub layers preserve the underlying artwork. The small hole remains fully clear.
    float[] rings={.22f,.208f,.195f,.183f,.161f,.148f,.136f};
    int[] colors={0x130b1721,0x40d9dfe3,0x13707d87,0x1cbbc4cb,0x1ce4e9ed,0x1378858f,0x25c6cfd6};
    for(int i=0;i<rings.length;i++)CIRCLE.invoke(scope,ColorKt.Color(colors[i]),r*rings[i],pack(cx,cy),1f,Fill.INSTANCE,null,3);
   }finally{canvas.restore();}
  }
   }
  }finally{moving.restore();}
  // Complete source fitted proportionally into its own square, drawn over CD.
  float cw=aspect>=1?side:side*aspect,ch=aspect>=1?side/aspect:side;
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{canvas.translate((side-cw)*.5f,(side-ch)*.5f);source.draw(scope,cw,ch);}finally{canvas.restore();}
 }
 static long pack(float x,float y){return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL);}
 static float x(long p){return Float.intBitsToFloat((int)(p>>>32));}
 static float y(long p){return Float.intBitsToFloat((int)p);}
}
