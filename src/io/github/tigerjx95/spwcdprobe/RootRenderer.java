package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.Canvas;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.ColorKt;
import androidx.compose.ui.graphics.Path;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.geometry.Rect;
import java.util.function.Supplier;
import androidx.compose.ui.graphics.drawscope.*;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
/** Draws into our own full-width Canvas. Never invoked inside the native Image node. */
final class RootRenderer {
 interface Source {void draw(DrawScope scope,float width,float height)throws Exception; default boolean blankDisc(){return false;}}
 interface Disc {boolean draw(DrawScope scope,float cx,float cy,float diameter)throws Exception;}
 private static final Method CLIP,CLIP_RECT,CIRCLE,BRUSH_CIRCLE,RADIAL,LINEAR,BOX_COLOR,SIZE,INTRINSIC,DRAW;
 private static final Constructor<Stroke> STROKE;
 private record Finish(float radius,Stroke line,Stroke edge,Brush hub,Brush metal,Brush innerLip,Stroke bevel,Stroke lip){}
 private static volatile Finish cachedFinish;
 private record VinylFinish(float radius,Path grooves,Path label,Path hole,Path spindleInset,Stroke grooveStroke,Stroke rim,Brush surface,Brush groovesLight,Brush falloff){}
 private static volatile VinylFinish cachedVinyl;
 private static final Method BRUSH_PATH,SWEEP;

 static {
  try{
   SWEEP=Brush.Companion.getClass().getMethod("sweepGradient-Uv8p0NA",kotlin.Pair[].class,long.class);
   BRUSH_PATH=DrawScope.class.getMethod("drawPath-GBMwjPU",Path.class,Brush.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
   CLIP_RECT=Canvas.class.getMethod("clipRect-N_I0leg",float.class,float.class,float.class,float.class,int.class);
   CLIP=Canvas.class.getMethod("clipPath-mtrdD-E",Path.class,int.class);
   CIRCLE=DrawScope.class.getMethod("drawCircle-VaOC9Bg",long.class,float.class,long.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
   BRUSH_CIRCLE=DrawScope.class.getMethod("drawCircle-V9BoPsw",Brush.class,float.class,long.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
   RADIAL=Brush.Companion.getClass().getMethod("radialGradient-P_Vx-Ks",kotlin.Pair[].class,long.class,float.class,int.class);
   LINEAR=Brush.Companion.getClass().getMethod("linearGradient-mHitzGk",kotlin.Pair[].class,long.class,long.class,int.class);
   BOX_COLOR=androidx.compose.ui.graphics.Color.class.getMethod("box-impl",long.class);
   SIZE=DrawScope.class.getMethod("getSize-NH-jbRc");
   INTRINSIC=PainterDrawBinding.INTRINSIC_SIZE;
   DRAW=PainterDrawBinding.METHOD;
   STROKE=Stroke.class.getConstructor(float.class,float.class,int.class,int.class,androidx.compose.ui.graphics.PathEffect.class,kotlin.jvm.internal.DefaultConstructorMarker.class);
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc)throws Exception{
  draw(scope,painter,enter,leave,disc,1f);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,DiscExposure.DEFAULT);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,false);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,front?new FrontDiscLayer():null);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,0f);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,angle,false);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean blank)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,angle,blank,false);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean blank,boolean vinyl)throws Exception{
  long intrinsic=(Long)INTRINSIC.invoke(painter);float iw=x(intrinsic),ih=y(intrinsic);
  if(!Float.isFinite(iw)||!Float.isFinite(ih)||iw<=0||ih<=0){iw=ih=1;}
  final float ratio=iw/ih;
  paint(scope,ratio,new Source(){
   public void draw(DrawScope s,float w,float h)throws Exception{enter.run();try{DRAW.invoke(painter,s,pack(w,h),1f,null);}finally{leave.run();}}
   public boolean blankDisc(){return blank;}
  },disc,SkiaBackedPath_skikoKt::Path,exposure,amount,front,layer,angle,vinyl);
 }
 static void paint(DrawScope scope,float aspect,Source source)throws Exception{paint(scope,aspect,source,(s,x,y,d)->false);}
 static void paint(DrawScope scope,float aspect,Source source,Disc disc)throws Exception{
  paint(scope,aspect,source,disc,SkiaBackedPath_skikoKt::Path);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths)throws Exception{
  paint(scope,aspect,source,disc,paths,1f);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,DiscExposure.DEFAULT);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,false);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,front?new FrontDiscLayer():null);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,0f);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,false);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean vinyl)throws Exception{
  exposure=Math.max(0,Math.min(1,exposure));
  long size=(Long)SIZE.invoke(scope);float w=x(size),h=y(size),side=Math.min(h,w/DiscExposure.widthFactor(amount));
  if(side<=0||!Float.isFinite(side))return;
  // The complete group fits its independently measured width at every amount.
  float cx=DiscExposure.center(side,exposure,amount),cy=side*.5f,r=side*.49f;
  if(front)cover(scope,aspect,source,side);
  Canvas moving=scope.getDrawContext().getCanvas();moving.save();
  try{
   // Front discs stay whole throughout their motion. Only the fixed Canvas
   // bounds clip them; there is no right-to-left reveal edge over the sleeve.
   CLIP_RECT.invoke(moving,front?0f:side,0f,w,side,1);
   if(exposure>0&&DiscExposure.fraction(amount)>0){
   boolean blended=front&&layer.begin(moving,w,side,exposure);
   moving.save();
   try{
    if(angle!=0f){moving.translate(cx,cy);moving.rotate(angle);moving.translate(-cx,-cy);}
  if(!disc.draw(scope,cx,cy,r*2f)){
   if(vinyl)vinylFace(scope,source,aspect,cx,cy,r,paths,angle);
   else{
   Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
   try{
    Path outer=paths.get();outer.addOval(new Rect(cx-r,cy-r,cx+r,cy+r),Path.Direction.Clockwise);
    Path hole=paths.get();float hr=r*.125f;hole.addOval(new Rect(cx-hr,cy-hr,cx+hr,cy+hr),Path.Direction.Clockwise);
    CLIP.invoke(canvas,outer,1); // Intersect with outer circle.
    CLIP.invoke(canvas,hole,0);  // Difference: genuine transparent center hole.
    // Fill the circle without stretching; crop only this secondary copy.
    float dw=aspect>=1?r*2*aspect:r*2,dh=aspect>=1?r*2:r*2/aspect;
    canvas.save();
    try{if(source.blankDisc())blankFace(scope,r,cx,cy);else{canvas.translate(cx-dw*.5f,cy-dh*.5f);source.draw(scope,dw,dh);}}finally{canvas.restore();}
    finish(scope,r,pack(cx,cy));
   }finally{canvas.restore();}
   // A faint translucent film keeps the background visible through the hole.
   CIRCLE.invoke(scope,ColorKt.Color(0x18ffffff),r*.125f,pack(cx,cy),1f,Fill.INSTANCE,null,3);
   // Keep the tiny spindle highlight.
   CIRCLE.invoke(scope,ColorKt.Color(0xccffffff),Math.max(.65f,r*.0075f),pack(cx,cy),1f,Fill.INSTANCE,null,3);
   }
  }
   }finally{moving.restore();if(blended)moving.restore();}
   }
  }finally{moving.restore();}
  if(!front)cover(scope,aspect,source,side);
 }
 private static Path oval(Supplier<Path> paths,float radius){
  Path path=paths.get();
  path.addOval(new Rect(-radius,-radius,radius,radius),Path.Direction.Clockwise);
  return path;
 }
 private static VinylFinish vinylFinish(float radius,Supplier<Path> paths)throws Exception{
  VinylFinish finish=cachedVinyl;
  if(finish!=null&&finish.radius()==radius)return finish;
  Path grooves=paths.get();
  for(int ring=0;ring<42;ring++){
   float r=radius*(.395f+ring*.0141f);
   grooves.addOval(new Rect(-r,-r,r,r),Path.Direction.Clockwise);
  }
  // Broad reflections above and below the hub, separated by near-black flanks.
  float[] angles={0,1/12f,1/6f,1/4f,1/3f,5/12f,.5f,7/12f,2/3f,.75f,5/6f,11/12f,1};
  Brush surface=(Brush)SWEEP.invoke(Brush.Companion,grayStops(angles,new int[]{12,21,40,63,40,21,11,19,37,60,38,21,12}),pack(0,0));
  Brush groovesLight=(Brush)SWEEP.invoke(Brush.Companion,grayStops(angles,new int[]{18,28,52,85,52,28,17,25,49,81,50,28,18}),pack(0,0));
  float[] stops={0,.38f,.54f,.71f,.87f,.98f,1};
  int[] colors={0x00000000,0x00000000,0x00000000,0x12000000,0x27000000,0x45000000,0x45000000};
  kotlin.Pair<?,?>[] shading=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++)shading[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  Brush falloff=(Brush)RADIAL.invoke(Brush.Companion,shading,pack(0,0),radius,0);
  return cachedVinyl=new VinylFinish(radius,grooves,oval(paths,radius*.375f),oval(paths,radius*.029f),oval(paths,radius*.038f),
      stroke(radius*.0065f),stroke(radius*.018f),surface,groovesLight,falloff);
 }
 private static kotlin.Pair<?,?>[] grayStops(float[] stops,int[] levels)throws Exception{
  kotlin.Pair<?,?>[] values=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++)values[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(0xff000000|levels[i]*0x010101)));
  return values;
 }
 private static void vinylFace(DrawScope scope,Source source,float aspect,float cx,float cy,float radius,Supplier<Path> paths,float angle)throws Exception{
  VinylFinish finish=vinylFinish(radius,paths);
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate(cx,cy);
   CLIP.invoke(canvas,finish.hole(),0);
   long center=pack(0,0);
   canvas.save();
   try{
    // The label rotates with the record, while the reflected light stays fixed.
    if(angle!=0f)canvas.rotate(-angle);
    BRUSH_CIRCLE.invoke(scope,finish.surface(),radius,center,1f,Fill.INSTANCE,null,3);
    BRUSH_PATH.invoke(scope,finish.grooves(),finish.groovesLight(),1f,finish.grooveStroke(),null,3);
    BRUSH_CIRCLE.invoke(scope,finish.falloff(),radius,center,1f,Fill.INSTANCE,null,3);
   }finally{canvas.restore();}
   circle(scope,0xff363636,radius-finish.rim().getWidth()*.5f,center,finish.rim());
   CIRCLE.invoke(scope,ColorKt.Color(0xff101010),radius*.386f,center,1f,Fill.INSTANCE,null,3);
   CIRCLE.invoke(scope,ColorKt.Color(0xff252525),radius*.375f,center,1f,Fill.INSTANCE,null,3);
   if(!source.blankDisc()){
    canvas.save();
    try{
     CLIP.invoke(canvas,finish.label(),1);
     CLIP.invoke(canvas,finish.spindleInset(),0);
     float size=radius*.75f;
     float w=size*Math.max(1f,aspect),h=size*Math.max(1f,1f/aspect);
     canvas.translate(-w*.5f,-h*.5f);
     source.draw(scope,w,h);
    }finally{canvas.restore();}
   }
   // Fill the annulus up to the cutout so label colors cannot show between the rim and hole.
   CIRCLE.invoke(scope,ColorKt.Color(0xffdedede),radius*.042f,center,1f,Fill.INSTANCE,null,3);
  }finally{canvas.restore();}
 }
 private static void blankFace(DrawScope scope,float r,float cx,float cy)throws Exception{
  // Uniform light silver-gray face: the reference has no directional light band.
  CIRCLE.invoke(scope,ColorKt.Color(0xffdce0e3),r,pack(cx,cy),1f,Fill.INSTANCE,null,3);
 }
 private static Stroke stroke(float width)throws Exception{return STROKE.newInstance(width,4f,0,0,null,null);}
 private static Brush hub(float r)throws Exception{
  // Narrow dark groove / bright lip pairs, with a milky translucent inner bevel.
  // Radial proportions stay fixed: no enlarged hub or thicker concentric outlines.
  float[] stops={0,.568f,.580f,.603f,.628f,.66f,.72f,.82f,.90f,.930f,.948f,.960f,.976f,.987f,1};
  int[] colors={0x00ffffff,0xa8f3f4f2,0xc8ffffff,0xb0f0f1ee,0x989ea8aa,0x90e5e7e3,0x78e5e7e4,0x70dbe1e2,0x88d3dce0,0xb064737e,0xe8f5f7f6,0xe03f4e59,0xf0eef3f5,0xd8ffffff,0x80505e67};
  kotlin.Pair<?,?>[] values=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++)values[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  return (Brush)RADIAL.invoke(Brush.Companion,values,pack(0,0),r*.22f,0);
 }
 private static Brush metal(float r)throws Exception{
  // Directional reflections: a restrained cool/warm pair flanking a white glint.
  float[] stops={0,.18f,.34f,.46f,.53f,.62f,.78f,1};
  int[] colors={0xa03c4b58,0xd8f7fafb,0x907f9cac,0xb8d4bd8d,0xe8ffffff,0x90495661,0xd8e8f1f7,0x98616c72};
  kotlin.Pair<?,?>[] values=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++)values[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  return (Brush)LINEAR.invoke(Brush.Companion,values,pack(-r*.18f,-r*.13f),pack(r*.18f,r*.13f),0);
 }
 private static Brush innerLip(float r)throws Exception{
  // A broad, low-contrast molded lip catches light opposite the metal edge.
  float[] stops={0,.3f,.55f,.78f,1};
  int[] colors={0x405c6970,0x30c8d0cf,0x60f1f2ee,0xa8ffffff,0x80e8ece9};
  kotlin.Pair<?,?>[] values=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++)values[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  return (Brush)LINEAR.invoke(Brush.Companion,values,pack(-r*.10f,-r*.10f),pack(r*.10f,r*.10f),0);
 }
 private static void finish(DrawScope scope,float r,long center)throws Exception{
  Finish style=cachedFinish;
  if(style==null||style.radius()!=r){
   float line=Math.max(.6f,Math.min(1.2f,r*.004f));
   cachedFinish=style=new Finish(r,stroke(line),stroke(Math.max(1.2f,Math.min(5f,r*.018f))),hub(r),metal(r),innerLip(r),stroke(r*.005f),stroke(r*.012f));
  }
  float line=style.line().getWidth();
  // White optical edge sits wholly inside the original disc silhouette.
  float edge=style.edge().getWidth();
  circle(scope,0xeefafcfd,r-edge*.5f,center,style.edge());
  circle(scope,0xafffffff,r-line*.5f,center,style.line());
  circle(scope,0x236b7480,Math.max(0,r-edge-line*.3f),center,style.line());
  // Soft translucent bevel replaces discrete nested outlines. Keep the brush
  // at a local origin so it can be reused while the disc moves across the page.
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate(x(center),y(center));
   BRUSH_CIRCLE.invoke(scope,style.hub(),r*.22f,pack(0,0),1f,Fill.INSTANCE,null,3);
   BRUSH_CIRCLE.invoke(scope,style.metal(),r*.210f,pack(0,0),1f,style.bevel(),null,3);
   // Reverse the adjacent polished lip so bright and dark edges separate.
   canvas.rotate(180f);
   BRUSH_CIRCLE.invoke(scope,style.metal(),r*.216f,pack(0,0),1f,style.bevel(),null,3);
   canvas.rotate(-180f);
   BRUSH_CIRCLE.invoke(scope,style.innerLip(),r*.134f,pack(0,0),1f,style.lip(),null,3);
  }finally{canvas.restore();}
 }
 private static void circle(DrawScope scope,int color,float radius,long center,Stroke style)throws Exception{
  CIRCLE.invoke(scope,ColorKt.Color(color),radius,center,1f,style,null,3);
 }
 private static void cover(DrawScope scope,float aspect,Source source,float side)throws Exception{
  // Complete source fitted proportionally into its own square, drawn over CD.
  float cw=aspect>=1?side:side*aspect,ch=aspect>=1?side/aspect:side;
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{canvas.translate((side-cw)*.5f,(side-ch)*.5f);source.draw(scope,cw,ch);}finally{canvas.restore();}
 }
 static long pack(float x,float y){return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL);}
 static float x(long p){return Float.intBitsToFloat((int)(p>>>32));}
 static float y(long p){return Float.intBitsToFloat((int)p);}
}
