package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.Canvas;
import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.ColorKt;
import androidx.compose.ui.graphics.Paint;
import androidx.compose.ui.graphics.Path;
import androidx.compose.ui.graphics.SkiaBackedCanvas;
import androidx.compose.ui.graphics.SkiaBackedCanvas_skikoKt;
import androidx.compose.ui.graphics.SkiaBackedPaint_skikoKt;
import androidx.compose.ui.graphics.SkiaBackedPath_skikoKt;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.geometry.RoundRectKt;
import java.util.function.Supplier;
import androidx.compose.ui.graphics.drawscope.*;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
/** Draws into our own full-width Canvas. Never invoked inside the native Image node. */
final class RootRenderer {
 interface Source {
  void draw(DrawScope scope,float width,float height)throws Exception;
  default boolean blankDisc(){return false;}
  default FramedArtwork vinylLabel(){return null;}
  default FramedArtwork discArtwork(){return null;}
 }
 record FramedArtwork(Source image,float aspect,VinylLabelFraming framing){}
 private static final class CoverCdHub {
  static final ImageBitmap MATERIAL=DiscImage.loadResource("/cd-material/hub.png");
  static void prepare(){} // Trigger the lazy decode while the artwork worker is already active.
 }
 interface Disc {
  boolean draw(DrawScope scope,float cx,float cy,float diameter)throws Exception;
  default boolean drawCoverCd(DrawScope scope,float cx,float cy,float diameter){return false;}
  default ImageBitmap coverCdHub(){return CoverCdHub.MATERIAL;}
  default boolean drawReelTape(DrawScope scope,float radius){return false;}
  default boolean drawVinylSurface(DrawScope scope,float radius){return false;}
 }
 static final class CoverLayer {
  private Path path;
  private float width,height,radius;
  private final float[] hoverMatrix=new float[16];
  float nativeScale=1f;
  CoverHover.Tilt tilt=CoverHover.Tilt.FLAT;
  void applyTilt(Canvas canvas,float width,float height)throws Exception{
   if(CONCAT==null||tilt==CoverHover.Tilt.FLAT||width<=0||height<=0)return;
   tilt.writeMatrix(hoverMatrix,width,height,nativeScale);
   canvas.translate(width*.5f,height*.5f);
   CONCAT.invoke(canvas,(Object)hoverMatrix);
   canvas.translate(-width*.5f,-height*.5f);
  }
  Path outline(Supplier<Path> paths,float width,float height,float radius){
   radius=Math.min(radius,Math.min(width,height)*.5f);
   if(path==null||this.width!=width||this.height!=height||this.radius!=radius){
    Path outline=paths.get();
    outline.addRoundRect(RoundRectKt.RoundRect(0,0,width,height,radius,radius),Path.Direction.Clockwise);
    path=outline;
    this.width=width;this.height=height;this.radius=radius;
   }
   return path;
  }
 }
 static final float CD_HOLE_RATIO=.125f;
 static final int CD_FACE_COLOR=0xffdce0e3, REEL_FIT_COLOR=0xff30383e, BLACK_LABEL_COLOR=0xff252525;
 private static final Method CONCAT,CLIP,CLIP_RECT,CIRCLE,BRUSH_CIRCLE,RADIAL,LINEAR,BOX_COLOR,SIZE,INTRINSIC,DRAW;
 private static final Constructor<Stroke> STROKE;
 private record Finish(float radius,Stroke line,Stroke blankEdge,Stroke coverEdge,Brush silver,Brush rim,
     Brush hub,Brush metal,Brush innerLip,Stroke bevel,Stroke lip){}
 private record VinylFinish(float radius,DiscSettings.VinylColor color,Path grooves,Path grooveWalls,Path label,Path hole,
     Path spindleInset,Stroke grooveStroke,Stroke grooveWallStroke,Stroke rim,int rimColor,Brush surface,
     Brush groovesLight,Brush grooveShade,Brush falloff,Brush reflection,Path reflectionClip){}
 // Offscreen preparation owns a separate cache until it hands its brushes to the UI thread.
 static final class Finishes {
  private volatile Finish cd;
  private volatile VinylFinish vinyl;
  private volatile ReelFinish reel;
  private org.jetbrains.skia.Paint cdRimPaint;
  private Paint vinylGroovePaint;
  org.jetbrains.skia.Paint cdRimPaint(){
   if(cdRimPaint==null){
    cdRimPaint=new org.jetbrains.skia.Paint();
    cdRimPaint.setMode(org.jetbrains.skia.PaintMode.STROKE);
    cdRimPaint.setAntiAlias(true);
   }
   return cdRimPaint;
  }
  Paint vinylGroovePaint(){
   if(vinylGroovePaint==null){
    var stroke=new org.jetbrains.skia.Paint();
    stroke.setMode(org.jetbrains.skia.PaintMode.STROKE);
    stroke.setAntiAlias(true);
    vinylGroovePaint=SkiaBackedPaint_skikoKt.asComposePaint(stroke);
   }
   return vinylGroovePaint;
  }
 }
 private static final Finishes liveFinishes=new Finishes();

 static void adoptPrepared(Finishes prepared){
  if(prepared.cd!=null)liveFinishes.cd=prepared.cd;
  if(liveFinishes.cdRimPaint==null)liveFinishes.cdRimPaint=prepared.cdRimPaint;
  if(prepared.vinyl!=null)liveFinishes.vinyl=prepared.vinyl;
  if(liveFinishes.vinylGroovePaint==null)liveFinishes.vinylGroovePaint=prepared.vinylGroovePaint;
  if(prepared.reel!=null)liveFinishes.reel=prepared.reel;
 }
 private static final Method BRUSH_PATH,SWEEP,APPLY_BRUSH;

 static {
  try{
   APPLY_BRUSH=Brush.class.getMethod("applyTo-Pq9zytI",long.class,Paint.class,float.class);
   SWEEP=Brush.Companion.getClass().getMethod("sweepGradient-Uv8p0NA",kotlin.Pair[].class,long.class);
   BRUSH_PATH=DrawScope.class.getMethod("drawPath-GBMwjPU",Path.class,Brush.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
   Method concat;
   try{concat=Canvas.class.getMethod("concat-58bKbWc",float[].class);}
   catch(NoSuchMethodException unavailable){concat=null;}
   CONCAT=concat;
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
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,angle,blank,vinyl,DiscSettings.VinylColor.BLACK);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean blank,boolean vinyl,DiscSettings.VinylColor color)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,angle,blank,
      vinyl?DiscSettings.Style.BLACK_VINYL:DiscSettings.Style.CD,color);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean blank,DiscSettings.Style style,DiscSettings.VinylColor color)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,angle,blank,style,color,DiscSettings.ReelColor.DEFAULT);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean blank,DiscSettings.Style style,DiscSettings.VinylColor color,DiscSettings.ReelColor reelColor)throws Exception{
  draw(scope,painter,enter,leave,disc,exposure,amount,front,layer,angle,blank,style,color,reelColor,null,0);
 }
 static void draw(DrawScope scope,Object painter,Runnable enter,Runnable leave,Disc disc,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean blank,DiscSettings.Style style,DiscSettings.VinylColor color,DiscSettings.ReelColor reelColor,CoverLayer coverLayer,float coverRadius)throws Exception{
  long intrinsic=(Long)INTRINSIC.invoke(painter);float iw=x(intrinsic),ih=y(intrinsic);
  if(!Float.isFinite(iw)||!Float.isFinite(ih)||iw<=0||ih<=0){iw=ih=1;}
  final float ratio=iw/ih;
  paint(scope,ratio,new Source(){
   public void draw(DrawScope s,float w,float h)throws Exception{enter.run();try{DRAW.invoke(painter,s,pack(w,h),1f,null);}finally{leave.run();}}
   public boolean blankDisc(){return blank;}
  },disc,SkiaBackedPath_skikoKt::Path,exposure,amount,front,layer,angle,style,color,false,reelColor,coverLayer,coverRadius);
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
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,vinyl,DiscSettings.VinylColor.BLACK);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean vinyl,DiscSettings.VinylColor color)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,vinyl,color,false);
 }
 // Populate the same caches used by the reveal, using the measured size.
 static void prepareDefaultDisc(DrawScope scope,int amount,float aspect,boolean trimmed,
      DiscSettings.Style style,DiscSettings.VinylColor vinylColor,DiscSettings.ReelColor reelColor,
      Supplier<Path> paths)throws Exception{
  long size=(Long)SIZE.invoke(scope);
  float side=Math.min(y(size),x(size)/DiscExposure.widthFactor(amount));
  if(side<=0||!Float.isFinite(side))return;
  float radius=discRadius(side,aspect,trimmed);
  if(style.reel()){
   reelFinish(radius,paths,reelColor,liveFinishes);
   ReelWordmark.prepare(style==DiscSettings.Style.REEL);
  }else if(style!=DiscSettings.Style.CD){
   vinylFinish(radius,paths,vinylColor,liveFinishes);
  }else discFinish(radius,liveFinishes);
 }
 // For a generated disc behind the sleeve, the reflection only samples a
 // bottom strip. Skip that disc draw until its circle can touch the strip to
 // the right of the sleeve. Include room for an antialiased edge.
 static float reflectedExposure(DrawScope scope,float exposure,int amount,float stripTop)throws Exception{
  if(exposure<=0)return exposure;
  long size=(Long)SIZE.invoke(scope);
  float side=Math.min(y(size),x(size)/DiscExposure.widthFactor(amount));
  if(side<=0||!Float.isFinite(side))return exposure;
  float dx=Math.max(0,side-DiscExposure.center(side,exposure,amount));
  float dy=Math.max(0,stripTop-side*.5f);
  float paddedRadius=side*.49f+2f;
  return dx*dx+dy*dy>paddedRadius*paddedRadius?0f:exposure;
 }
 static float discRadius(float side,float aspect,boolean trimmed){
  return (trimmed?Math.min(side,fittedHeight(side,aspect)):side)*.49f;
 }
 static float fittedWidth(float side,float aspect){return side*Math.min(1f,aspect);}
 static float fittedHeight(float side,float aspect){return side/Math.max(1f,aspect);}
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,boolean vinyl,DiscSettings.VinylColor color,boolean fitTrimmed)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,
      vinyl?DiscSettings.Style.BLACK_VINYL:DiscSettings.Style.CD,color,fitTrimmed);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,DiscSettings.Style style,DiscSettings.VinylColor color,boolean fitTrimmed)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,style,color,fitTrimmed,DiscSettings.ReelColor.DEFAULT);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,DiscSettings.Style style,DiscSettings.VinylColor color,boolean fitTrimmed,DiscSettings.ReelColor reelColor)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,style,color,fitTrimmed,reelColor,null,0);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,DiscSettings.Style style,DiscSettings.VinylColor color,boolean fitTrimmed,DiscSettings.ReelColor reelColor,CoverLayer coverLayer,float coverRadius)throws Exception{
  paint(scope,aspect,source,disc,paths,exposure,amount,front,layer,angle,style,color,fitTrimmed,reelColor,coverLayer,coverRadius,liveFinishes);
 }
 static void paint(DrawScope scope,float aspect,Source source,Disc disc,Supplier<Path> paths,float exposure,int amount,boolean front,FrontDiscLayer layer,float angle,DiscSettings.Style style,DiscSettings.VinylColor color,boolean fitTrimmed,DiscSettings.ReelColor reelColor,CoverLayer coverLayer,float coverRadius,Finishes finishes)throws Exception{
  style=style.forMissingCover(source.blankDisc());
  exposure=Math.max(0,Math.min(1,exposure));
  long size=(Long)SIZE.invoke(scope);float w=x(size),h=y(size),side=Math.min(h,w/DiscExposure.widthFactor(amount));
  if(side<=0||!Float.isFinite(side))return;
  // The complete group fits its independently measured width at every amount.
  float coverWidth=fittedWidth(side,aspect),coverHeight=fittedHeight(side,aspect);
  float right=fitTrimmed?(side+coverWidth)*.5f:side;
  float discSide=fitTrimmed?Math.min(side,coverHeight):side;
  float r=discRadius(side,aspect,fitTrimmed),cy=side*.5f;
  float travel=discSide*DiscExposure.fraction(amount);
  float cx=DiscExposure.center(side,exposure,amount);
  if(fitTrimmed){
   float closed=right-r;
   if(front&&closed<r){
    // A narrow sleeve cannot hide a full-height front disc. Keep the circle
    // inside the canvas throughout the fade, preserving its open position
    // whenever there is room for that position.
    float open=Math.max(r,closed+travel);
    closed=Math.min(side*.5f,open);
    cx=closed+(open-closed)*exposure;
   }else cx=closed+travel*exposure;
  }
  if(front)cover(scope,source,paths,side,coverWidth,coverHeight,coverLayer,coverRadius);
  Canvas moving=scope.getDrawContext().getCanvas();moving.save();
  try{
   // Front discs stay whole throughout their motion. Only the fixed Canvas
   // bounds clip them; there is no right-to-left reveal edge over the sleeve.
   CLIP_RECT.invoke(moving,front?0f:right,0f,w,side,1);
   if(exposure>0&&DiscExposure.fraction(amount)>0){
    boolean blended=front&&layer.begin(moving,w,side,exposure);
    try{
     boolean coverCd=style==DiscSettings.Style.CD&&!source.blankDisc();
     boolean drawn;
     moving.save();
     try{
      if(angle!=0f){moving.translate(cx,cy);moving.rotate(angle);moving.translate(-cx,-cy);}
      drawn=disc.draw(scope,cx,cy,r*2f);
      if(!drawn&&!coverCd&&!style.vinyl()){
       if(style.reel())reelFace(scope,cx,cy,r,reelFinish(r,paths,reelColor,finishes),style==DiscSettings.Style.REEL_COVER?source:null,aspect,angle,reelColor,disc);
       else blankCd(scope,cx,cy,r,paths,finishes);
      }
     }finally{moving.restore();}
     if(!drawn){
      if(coverCd)coverCd(scope,source,aspect,cx,cy,r,angle,disc,paths,finishes);
      else if(style.vinyl())vinylFace(scope,source,aspect,cx,cy,r,paths,angle,color,finishes,disc);
     }
    }finally{if(blended)moving.restore();}
   }
  }finally{moving.restore();}
  if(!front)cover(scope,source,paths,side,coverWidth,coverHeight,coverLayer,coverRadius);
 }
 static void bakeCdArtwork(DrawScope scope,Source source,float aspect,float side,Supplier<Path> paths)throws Exception{
  CoverCdHub.prepare();
  var canvas=scope.getDrawContext().getCanvas();
  float radius=side*.5f;
  canvas.save();
  try{
   canvas.translate(radius,radius);
   CLIP_RECT.invoke(canvas,-radius,-radius,radius,radius,1);
   paintCdArtwork(scope,canvas,source,aspect,radius,paths,discFinish(radius,new Finishes()));
  }finally{canvas.restore();}
 }
 private static void coverCd(DrawScope scope,Source source,float aspect,float cx,float cy,float radius,float angle,
     Disc disc,Supplier<Path> paths,Finishes finishes)throws Exception{
  var canvas=scope.getDrawContext().getCanvas();
  var style=discFinish(radius,finishes);
  canvas.save();
  try{
   canvas.translate(cx,cy);
   canvas.save();
   try{
    // Clip in screen orientation, with the art edge tucked underneath the white rim.
    float inset=Math.min(.75f,style.coverEdge().getWidth()*.3f);
    CLIP.invoke(canvas,oval(paths,radius-inset),1);
    if(angle!=0f)canvas.rotate(angle);
    if(!disc.drawCoverCd(scope,0,0,radius*2))paintCdArtwork(scope,canvas,source,aspect,radius,paths,style);
   }finally{canvas.restore();}
   // The artwork rotates beneath the fixed light. Both artwork paths use this pass.
   var hub=disc.coverCdHub();
   if(hub!=null){
    int size=hub.getWidth();
    float scale=radius*.42f/size;
    canvas.save();
    try{
     canvas.scale(scale,scale);
     DiscImage.drawBitmap(scope,hub,size,size,0,0,size);
    }finally{canvas.restore();}
   }
   paintCoverCdRim(scope,canvas,radius,style,finishes);
  }finally{canvas.restore();}
 }
 private static void paintCdArtwork(DrawScope scope,Canvas canvas,Source source,float aspect,float radius,
     Supplier<Path> paths,Finish style)throws Exception{
  canvas.save();
  try{
   CLIP.invoke(canvas,oval(paths,radius*CD_HOLE_RATIO),0);
   var artwork=source.discArtwork();
   // Fit-mode backing extends over the square raster. The round boundary is applied on screen.
   if(artwork!=null&&artwork.framing().fit())circle(scope,CD_FACE_COLOR,radius*1.42f,pack(0,0),Fill.INSTANCE);
   canvas.save();
   try{drawDiscArtwork(scope,source,artwork,aspect,0,0,radius*2);}finally{canvas.restore();}
   paintCdHub(scope,canvas,radius,style);
  }finally{canvas.restore();}
  paintCdHole(scope,radius,pack(0,0));
 }
 private static void blankCd(DrawScope scope,float cx,float cy,float radius,Supplier<Path> paths,Finishes finishes)throws Exception{
  var canvas=scope.getDrawContext().getCanvas();
  var style=discFinish(radius,finishes);
  canvas.save();
  try{
   canvas.translate(cx,cy);
   CLIP.invoke(canvas,oval(paths,radius),1);
   CLIP.invoke(canvas,oval(paths,radius*CD_HOLE_RATIO),0);
   BRUSH_CIRCLE.invoke(scope,style.silver(),radius,pack(0,0),1f,Fill.INSTANCE,null,3);
   paintBlankCdRim(scope,radius,style);
   paintCdHub(scope,canvas,radius,style);
  }finally{canvas.restore();}
  paintCdHole(scope,radius,pack(cx,cy));
 }
 private static void paintCdHole(DrawScope scope,float radius,long center)throws Exception{
  // A faint translucent film keeps the background visible through the hole.
  circle(scope,0x18ffffff,radius*CD_HOLE_RATIO,center,Fill.INSTANCE);
  circle(scope,0xccffffff,Math.max(.65f,radius*.0075f),center,Fill.INSTANCE);
 }
 private static void drawDiscArtwork(DrawScope scope,Source source,FramedArtwork artwork,float aspect,float cx,float cy,float diameter)throws Exception{
  var framing=artwork==null?VinylLabelFraming.DEFAULT:artwork.framing();
  var box=framing.place(artwork==null?aspect:artwork.aspect(),diameter);
  scope.getDrawContext().getCanvas().translate(cx+box.x(),cy+box.y());
  (artwork==null?source:artwork.image()).draw(scope,box.width(),box.height());
 }
 private static Path oval(Supplier<Path> paths,float radius){
  Path path=paths.get();
  path.addOval(new Rect(-radius,-radius,radius,radius),Path.Direction.Clockwise);
  return path;
 }
 private static VinylFinish vinylFinish(float radius,Supplier<Path> paths,DiscSettings.VinylColor color,Finishes finishes)throws Exception{
  VinylFinish finish=finishes.vinyl;
  if(finish!=null&&finish.radius()==radius&&finish.color()==color)return finish;
  boolean colored=color!=DiscSettings.VinylColor.BLACK;
  Path grooves=paths.get();
  Path grooveWalls=paths.get();
  float grooveWidth=colored?Math.max(.72f,radius*.0033f):Math.max(.65f,Math.min(3.3f,radius*.0047f));
  if(colored){
   addColoredVinylGrooves(grooves,grooveWalls,radius,grooveWidth);
  }else{
   // Keep enough screen-space separation for grooves on small discs.
   float recordBand=.58f;
   int count=Math.max(1,Math.min(48,(int)(radius*recordBand/2.6f)));
   float pitch=recordBand/count;
   for(int ring=0;ring<count;ring++){
    float spacingOffset=.08f*(float)Math.sin(ring*.82f);
    float r=radius*(.397f+(ring+.5f+spacingOffset)*pitch);
    grooves.addOval(new Rect(-r,-r,r,r),Path.Direction.Clockwise);
    float wall=r-grooveWidth*.72f;
    grooveWalls.addOval(new Rect(-wall,-wall,wall,wall),Path.Direction.Clockwise);
   }
  }
  float[] angles={0,1/12f,1/6f,1/4f,1/3f,5/12f,.5f,7/12f,2/3f,.75f,5/6f,11/12f,1};
  float depth=colored?vinylPigmentDepth(color):0f;
  Brush surface=(Brush)SWEEP.invoke(Brush.Companion,
      colored?coloredVinylBody(color,angles,depth)
             :vinylStops(color,angles,new int[]{13,23,42,58,42,23,12,21,40,55,40,22,13},0xff,0),pack(0,0));
  Brush groovesLight=(Brush)SWEEP.invoke(Brush.Companion,
      colored?coloredVinylGlints(color,depth)
             :vinylStops(color,angles,new int[]{18,31,59,85,59,32,17,29,57,84,57,30,18},0xc4,0),pack(0,0));
  Brush grooveShade=(Brush)SWEEP.invoke(Brush.Companion,
      colored?coloredVinylGrooveShade(color,depth)
             :vinylStops(color,angles,new int[]{7,10,17,22,17,10,7,10,16,21,16,10,7},0xba,0),pack(0,0));
  float[] stops=colored?new float[]{0,.375f,.415f,.444f,.475f,.65f,.89f,.95f,.982f,1}
                       :new float[]{0,.38f,.54f,.71f,.87f,.98f,1};
  // Match perceptual lightness and chroma across the six translucent runout tints.
  int runout=switch(color){
   case BLUE, RANDOM -> 0x35bfe5;
   case GREEN -> 0x60c68f;
   case AMBER -> 0xd7a447;
   case PURPLE -> 0xc398ec;
   case PINK -> 0xea8cb6;
   case RED -> 0xf38c8d;
   case BLACK -> 0;
  };
  int ringStart=0x7c000000|runout;
  int[] colors=colored?new int[]{ringStart,ringStart,0x76000000|runout,
                                 0x36000000|runout,0x06000000|runout,0x06000000,0,0x06000000,0x1b000000,0x23000000}
                      :new int[]{0x00000000,0x00000000,0x00000000,0x12000000,0x27000000,0x45000000,0x45000000};
  Brush falloff=(Brush)RADIAL.invoke(Brush.Companion,gradientStops(stops,colors),pack(0,0),radius,0);
  Brush reflection=colored?null:(Brush)RADIAL.invoke(Brush.Companion,
      gradientStops(new float[]{0,.28f,.58f,.83f,1},new int[]{0x26e9e9e9,0x1de9e9e9,0x0ee9e9e9,0x04e9e9e9,0x00e9e9e9}),
      pack(0,0),radius*.482f,0);
  int rimColor=colored?((138+Math.round(depth*18f))<<24)|(color.shade(84)&0xffffff):color.shade(54);
  return finishes.vinyl=new VinylFinish(radius,color,grooves,grooveWalls,oval(paths,radius*.375f),oval(paths,radius*.029f),
      oval(paths,radius*.038f),stroke(grooveWidth),stroke(grooveWidth*(colored?1.08f:.82f)),
      stroke(radius*(colored?.013f:.018f)),rimColor,surface,groovesLight,grooveShade,falloff,reflection,colored?null:oval(paths,radius));
 }
 private static void addColoredVinylGrooves(Path grooves,Path grooveWalls,float radius,float width){
  // Smooth track breaks interrupt the finely spaced grooves.
  float[] starts={.478f,.669f,.785f,.900f};
  float[] ends={.656f,.772f,.886f,.974f};
  for(int band=0;band<starts.length;band++){
   float span=ends[band]-starts[band];
   float minPitch=Math.max(radius/108f,2.95f);
   int count=1+(int)(radius*span/minPitch);
   float pitch=count==1?0:span/(count-1);
   float first=starts[band]+(count==1?span*.5f:0);
   for(int ring=0;ring<count;ring++){
    float r=radius*(first+ring*pitch);
    float wall=r-width*.76f;
    grooves.addOval(new Rect(-r,-r,r,r),Path.Direction.Clockwise);
    grooveWalls.addOval(new Rect(-wall,-wall,wall,wall),Path.Direction.Clockwise);
   }
  }
 }
 private static float vinylPigmentDepth(DiscSettings.VinylColor color){
  int rgb=color.shade(85);
  int lightness=(54*(rgb>>16&255)+183*(rgb>>8&255)+19*(rgb&255)+128)>>8;
  // Normalize the existing palette's lightness range. Deeper pigments keep more color over bright artwork.
  return Math.max(0f,Math.min(1f,(192-lightness)/76f));
 }
 private static kotlin.Pair<?,?>[] vinylStops(DiscSettings.VinylColor color,float[] stops,int[] levels,int alpha,int lift)throws Exception{
  int[] colors=new int[levels.length];
  for(int i=0;i<levels.length;i++)colors[i]=(alpha<<24)|(color.shade(Math.min(85,levels[i]+lift))&0xffffff);
  return gradientStops(stops,colors);
 }
 private static kotlin.Pair<?,?>[] coloredVinylBody(DiscSettings.VinylColor color,float[] angles,float depth)throws Exception{
  int baseAlpha=92+Math.round(depth*13f);
  int lift=Math.round(depth*4f);
  int[] levels={69,74,81,85,79,72,68,72,80,85,78,72,69};
  int[] sheen={0,2,10,22,8,2,0,2,10,19,9,2,0};
  int[] colors=new int[levels.length];
  for(int i=0;i<levels.length;i++)colors[i]=((baseAlpha+sheen[i])<<24)|(color.shade(Math.min(85,levels[i]+lift))&0xffffff);
  return gradientStops(angles,colors);
 }
 private static kotlin.Pair<?,?>[] coloredVinylGrooveShade(DiscSettings.VinylColor color,float depth)throws Exception{
  float[] angles={0,.06f,.115f,.17f,.23f,.5f,.555f,.615f,.675f,.74f,1};
  int[] levels={17,25,40,26,16,17,26,46,29,16,17};
  int pigment=color.shade(27)&0xffffff;
  int[] colors=new int[levels.length];
  for(int i=0;i<levels.length;i++)colors[i]=(Math.round(levels[i]*(1f+depth*.13f))<<24)|pigment;
  return gradientStops(angles,colors);
 }
 private static kotlin.Pair<?,?>[] coloredVinylGlints(DiscSettings.VinylColor color,float depth)throws Exception{
  float[] positions={0,.06f,.095f,.115f,.14f,.18f,.25f,.50f,.55f,.59f,.615f,.64f,.68f,.75f,1};
  int[] strengths={24,33,48,65,52,38,25,24,39,65,90,71,45,26,24};
  int rgb=color.shade(85);
  // Keep the pigment in the groove reflections, with a little more light for deep colors.
  float light=.44f+depth*.13f;
  int red=vinylTint(rgb>>16,light),green=vinylTint(rgb>>8,light),blue=vinylTint(rgb,light);
  int glint=red<<16|green<<8|blue;
  int[] colors=new int[strengths.length];
  for(int i=0;i<strengths.length;i++)colors[i]=(Math.round(strengths[i]*(1f+depth*.22f))<<24)|glint;
  return gradientStops(positions,colors);
 }
 private static int vinylTint(int channel,float light){
  int value=channel&255;
  return Math.round(value+(255-value)*light);
 }
 static void bakeVinylSurface(DrawScope scope,float radius,Supplier<Path> paths,DiscSettings.VinylColor color)throws Exception{
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate(radius,radius);
   var finishes=new Finishes();
   paintVinylSurface(scope,canvas,radius,vinylFinish(radius,paths,color,finishes),finishes);
  }finally{canvas.restore();}
 }
 private static void paintVinylSurface(DrawScope scope,Canvas canvas,float radius,VinylFinish finish,Finishes finishes)throws Exception{
  long center=pack(0,0);
  BRUSH_CIRCLE.invoke(scope,finish.surface(),radius,center,1f,Fill.INSTANCE,null,3);
  if(finish.reflection()!=null){
   BRUSH_CIRCLE.invoke(scope,finish.falloff(),radius,center,1f,Fill.INSTANCE,null,3);
   // The softbox and weaker bounce light share an axis. Both fade before the original runout.
   canvas.save();
   try{
    CLIP.invoke(canvas,finish.reflectionClip(),1);
    canvas.rotate(42f);
    canvas.scale(1f,.40f);
    canvas.translate(-radius*.93f,0);
    BRUSH_CIRCLE.invoke(scope,finish.reflection(),radius*.482f,center,1f,Fill.INSTANCE,null,3);
    canvas.translate(radius*1.86f,0);
    BRUSH_CIRCLE.invoke(scope,finish.reflection(),radius*.482f,center,.42f,Fill.INSTANCE,null,3);
   }finally{canvas.restore();}
  }
  paintVinylGrooves(scope,canvas,finish.grooveWalls(),finish.grooveShade(),finish.grooveWallStroke(),finishes);
  paintVinylGrooves(scope,canvas,finish.grooves(),finish.groovesLight(),finish.grooveStroke(),finishes);
  // On colored vinyl the original radial finish covers both the runout and grooves.
  if(finish.reflection()==null)
   BRUSH_CIRCLE.invoke(scope,finish.falloff(),radius,center,1f,Fill.INSTANCE,null,3);
 }
 private static void paintVinylGrooves(DrawScope scope,Canvas canvas,Path path,Brush brush,Stroke stroke,Finishes finishes)throws Exception{
  if(canvas instanceof SkiaBackedCanvas){
   var paint=finishes.vinylGroovePaint();
   APPLY_BRUSH.invoke(brush,SIZE.invoke(scope),paint,1f);
   var nativePaint=SkiaBackedPaint_skikoKt.getSkiaPaint(paint);
   nativePaint.setStrokeWidth(stroke.getWidth());
   SkiaBackedCanvas_skikoKt.getNativeCanvas(canvas).drawPath(SkiaBackedPath_skikoKt.materializeSkiaPath(path),nativePaint);
  }else BRUSH_PATH.invoke(scope,path,brush,1f,stroke,null,3);
 }
 private static void vinylFace(DrawScope scope,Source source,float aspect,float cx,float cy,float radius,Supplier<Path> paths,
      float angle,DiscSettings.VinylColor color,Finishes finishes,Disc disc)throws Exception{
  VinylFinish finish=vinylFinish(radius,paths,color,finishes);
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate(cx,cy);
   CLIP.invoke(canvas,finish.hole(),0);
   long center=pack(0,0);
   // Draw the surface directly in screen orientation, without a rotation to undo.
   if(!disc.drawVinylSurface(scope,radius))paintVinylSurface(scope,canvas,radius,finish,finishes);
   boolean classic=color==DiscSettings.VinylColor.BLACK;
   circle(scope,finish.rimColor(),radius-finish.rim().getWidth()*.5f,center,finish.rim());
   if(classic){
    CIRCLE.invoke(scope,ColorKt.Color(0xff101010),radius*.386f,center,1f,Fill.INSTANCE,null,3);
    CIRCLE.invoke(scope,ColorKt.Color(0xff252525),radius*.375f,center,1f,Fill.INSTANCE,null,3);
   }
   if(!source.blankDisc()){
    canvas.save();
    try{
     if(angle!=0f)canvas.rotate(angle);
     CLIP.invoke(canvas,finish.label(),1);
     CLIP.invoke(canvas,finish.spindleInset(),0);
     float size=radius*.75f;
     var artwork=source.vinylLabel();
     if(artwork!=null&&artwork.framing().fit())
      circle(scope,classic?BLACK_LABEL_COLOR:color.shade(65),size*.5f,center,Fill.INSTANCE);
     drawDiscArtwork(scope,source,artwork,aspect,0,0,size);
    }finally{canvas.restore();}
   }
   // Fill the annulus up to the cutout so label colors cannot show between the rim and hole.
   CIRCLE.invoke(scope,ColorKt.Color(0xffdedede),radius*.042f,center,1f,Fill.INSTANCE,null,3);
  }finally{canvas.restore();}
 }
 private record ReelTape(Brush body,Path grooves,Path edges,Stroke line,Brush grooveInk,Brush edgeInk,Brush sheen){}
 private record ReelFinish(float radius,DiscSettings.ReelColor color,Path outer,Path artworkFace,Path windows,Path spindle,Stroke rim,
      ReelTape tape,Brush hubGlass,Brush glassReflection,Brush glassInnerEdge,Stroke windowLip,Brush windowShade,
      Brush windowSoftShadow,Brush windowContactShadow,Brush windowHighlight,ReelMark mark,
      Stroke flange,Stroke flangeSeam,Brush flangeMetal,Brush flangeBevel,Brush hubBevel,Brush hubRecess,Brush spindleBevel,
      Brush solidFace,Brush solidTrim,Brush glassBevel,Stroke collarBevel,Stroke reflectionWidth,Stroke rimHighlight){}
 private static ReelFinish reelFinish(float radius,Supplier<Path> paths,DiscSettings.ReelColor color,Finishes finishes)throws Exception{
  ReelFinish finish=finishes.reel;
  if(finish!=null&&finish.radius()==radius&&finish.color()==color)return finish;
  finish=buildReelFinish(radius,paths,color);
  finishes.reel=finish;
  return finish;
 }
 private static ReelTape buildReelTape(float radius,Supplier<Path> paths)throws Exception{
  Brush tape=(Brush)RADIAL.invoke(Brush.Companion,gradientStops(
      new float[]{0,.43f,.46f,.56f,.72f,.82f,.85f,.89f},
      new int[]{0xff181411,0xff191511,0xff2b211a,0xff413128,0xff5c4939,0xff4d3c30,0xff30251e,0xff181310}),
      pack(0,0),radius,0);
  // Each winding edge catches a little light directly beside the dark seam.
  Path tapeGrooves=paths.get(),tapeEdges=paths.get();
  for(int turn=0;turn<48;turn++){
   float groove=radius*(.438f+turn*.009f+(turn%4)*.00027f);
   float edge=groove+radius*.0035f;
   tapeGrooves.addOval(new Rect(-groove,-groove,groove,groove),Path.Direction.Clockwise);
   tapeEdges.addOval(new Rect(-edge,-edge,edge,edge),Path.Direction.Clockwise);
  }
  Brush sheen=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.12f,.23f,.30f,.42f,.54f,.65f,.74f,.88f,1},
      new int[]{0x04e8b78f,0x2de8b78f,0x04e8b78f,0x260b0806,0x27e8b78f,
                0x04e8b78f,0x220b0806,0x22e8b78f,0x1f0b0806,0x04e8b78f}),
      pack(0,0));
  return new ReelTape(tape,tapeGrooves,tapeEdges,stroke(radius*.0024f),reelInk(0x76140c08),reelInk(0x46c99469),sheen);
 }
 private static ReelFinish buildReelFinish(float radius,Supplier<Path> paths,DiscSettings.ReelColor color)throws Exception{
  Path windows=paths.get();
  ReelGeometry.windows(radius,new ReelGeometry.WindowPath(){
   public void moveTo(float x,float y){windows.moveTo(x,y);}
   public void lineTo(float x,float y){windows.lineTo(x,y);}
   public void corner(float x,float y,float nextX,float nextY){windows.quadraticTo(x,y,nextX,nextY);}
   public void arc(float r,float start,float sweep){windows.arcTo(new Rect(-r,-r,r,r),start,sweep,false);}
   public void close(){windows.close();}
  });
  Brush solidFace=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.075f,.19f,.315f,.44f,.565f,.69f,.815f,.94f,1},
      new int[]{tone(color.rgb,0),tone(color.rgb,color.sheen),tone(color.rgb,-color.sheen/2),
                tone(color.rgb,color.sheen),tone(color.rgb,-color.sheen/2),tone(color.rgb,color.sheen),
                tone(color.rgb,-color.sheen/2),tone(color.rgb,color.sheen),tone(color.rgb,-color.sheen/2),tone(color.rgb,0)}),pack(0,0));
  Brush hubGlass=(Brush)RADIAL.invoke(Brush.Companion,gradientStops(
      new float[]{0,.63f,.77f,.88f,.94f,1},
      new int[]{0x1015222c,0x1215222c,0x0c1d2932,0x102b3944,0x38202e37,0x58202e37}),
      pack(0,0),radius*.338f,0);
  Brush glassReflection=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.05f,.11f,.17f,.25f,.49f,.55f,.62f,.70f,.81f,1},
      new int[]{0x00f5fbff,0x00f5fbff,0x28f5fbff,0x06f5fbff,0x00f5fbff,0x00f5fbff,
                0x28f5fbff,0xa0f5fbff,0x1af5fbff,0x00f5fbff,0x00f5fbff}),pack(0,0));
  Brush glassInnerEdge=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.14f,.29f,.50f,.63f,.79f,1},
      new int[]{0x1af5fbff,0x54f5fbff,0x29f5fbff,0x24232f38,0xa2f5fbff,0x20f5fbff,0x1af5fbff}),pack(0,0));
  Brush glassBevel=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.18f,.38f,.50f,.62f,.72f,.85f,1},
      new int[]{0xff494e52,0xff373b3f,0xff4a4e52,0xff646b70,0xffacb1b5,0xff666d73,0xff454a4f,0xff494e52}),
      pack(0,0));
  Brush windowShade=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.22f,.39f,.57f,.72f,1},
      new int[]{0x9a140d09,0x521b130e,0xa4140d09,0x511b130e,0xa1140d09,0x9a140d09}),
      pack(0,0));
  Brush flangeMetal=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.19f,.36f,.50f,.62f,.71f,.83f,1},
      new int[]{0xff555652,0xff434440,0xff646460,0xff676762,0xffb3afa5,0xff92918a,0xff6d6e69,0xff555652}),
      pack(0,0));
  Brush flangeBevel=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,.24f,.48f,.59f,.66f,.79f,1},
      new int[]{0xff737470,0xff585955,0xff83847d,0xffb9b5ab,0xffc7c2b6,0xff91918a,0xff737470}),
      pack(0,0));
  float[] metalStops={0,.13f,.32f,.48f,.62f,.74f,.86f,1};
  Brush hubBevel=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(metalStops,
      new int[]{0xff626970,0xff3a3e44,0xff52575c,0xff7a8085,0xffb2b9be,0xff8f979d,0xff747b82,0xff626970}),pack(0,0));
  Brush hubRecess=(Brush)RADIAL.invoke(Brush.Companion,gradientStops(
      new float[]{0,.55f,.82f,.95f,1},
      new int[]{0xff25272a,0xff27292c,0xff222427,0xff191b1e,0xff101215}),pack(0,0),radius*.176f,0);
  Brush spindleBevel=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(metalStops,
      new int[]{0xff51565b,0xffa0a6aa,0xff787e83,0xff495055,0xff30353a,0xff4e555b,0xff656c72,0xff51565b}),pack(0,0));
  Brush solidTrim=(Brush)SWEEP.invoke(Brush.Companion,gradientStops(metalStops,
      new int[]{tone(color.trim,8),tone(color.trim,-26),tone(color.trim,-12),tone(color.trim,22),
                tone(color.trim,48),tone(color.trim,32),tone(color.trim,-14),tone(color.trim,8)}),pack(0,0));
  // The face overlaps the metal's inner edge (.970r) but stops beneath the opaque flange.
  return new ReelFinish(radius,color,oval(paths,radius),oval(paths,radius*ReelGeometry.FACE_RADIUS),windows,oval(paths,radius*ReelGeometry.SPINDLE_RADIUS),
      stroke(radius*.009f),buildReelTape(radius,paths),hubGlass,glassReflection,glassInnerEdge,stroke(radius*.036f),windowShade,
      reelInk(0x80100b08),reelInk(0xae080503),reelInk(0x94d7c7b2),reelMark(paths),
      stroke(radius*.028f),stroke(radius*.038f),flangeMetal,flangeBevel,hubBevel,hubRecess,spindleBevel,solidFace,solidTrim,glassBevel,
      stroke(radius*.020f),stroke(radius*.035f),stroke(radius*.0038f));
 }
 private static int tone(int rgb,int delta){
  return 0xff000000|channel(rgb>>16,delta)<<16|channel(rgb>>8,delta)<<8|channel(rgb,delta);
 }
 private static int channel(int rgb,int delta){return Math.max(0,Math.min(255,(rgb&255)+delta));}
 private record ReelMark(Path hairlines,Path downstrokes,Stroke fine,Stroke broad,
      Brush darkInk,Brush lightInk){}
 private static ReelMark reelMark(Supplier<Path> paths)throws Exception{
  Path mark=paths.get(),downstrokes=paths.get();
  // Hand-lettered Salt Player signature, shared by the face and the hub.
  // The capital S opens into a loop and curls back into its upper stroke.
  mark.moveTo(-4.72f,.40f);
  mark.cubicTo(-4.33f,.04f,-4.81f,-.37f,-5.39f,-.10f);
  mark.cubicTo(-6.04f,.20f,-5.84f,.89f,-5.20f,.83f);
  downstrokes.moveTo(-5.20f,.83f);
  downstrokes.cubicTo(-4.57f,.77f,-3.89f,.37f,-4.03f,-.21f);
  mark.moveTo(-4.03f,-.21f);
  mark.cubicTo(-4.18f,-.82f,-3.73f,-1.14f,-3.46f,-1.00f);
  mark.cubicTo(-3.22f,-.86f,-3.63f,-.43f,-3.85f,-.56f);
  mark.moveTo(-3.17f,.02f);
  mark.quadraticTo(-3.44f,-.38f,-3.8f,.08f);
  mark.quadraticTo(-4.13f,.59f,-3.77f,.57f);
  mark.quadraticTo(-3.52f,.56f,-3.17f,.02f);
  mark.quadraticTo(-3.68f,1.04f,-2.93f,.3f);
  mark.quadraticTo(-2.24f,-.44f,-2.34f,-.77f);
  mark.quadraticTo(-2.47f,-1.02f,-2.94f,.29f);
  mark.quadraticTo(-3.08f,.81f,-2.47f,.34f);
  mark.moveTo(-1.97f,-.51f);
  mark.quadraticTo(-2.73f,.85f,-2.22f,.58f);
  mark.quadraticTo(-2.04f,.49f,-1.83f,.28f);
  mark.moveTo(-2.54f,-.05f);
  mark.quadraticTo(-2.16f,-.12f,-1.84f,-.08f);

  // The P's descending stroke is part of the letter, with no separate underline.
  downstrokes.moveTo(-.45f,-.97f);
  downstrokes.cubicTo(-.78f,-.69f,-1.24f,.18f,-1.69f,.88f);
  mark.moveTo(-1.69f,.88f);
  mark.cubicTo(-2.01f,1.36f,-2.54f,1.45f,-2.42f,1.12f);
  mark.moveTo(-1.96f,-.40f);
  mark.cubicTo(-1.86f,-.91f,-.94f,-1.24f,-.37f,-1.09f);
  mark.cubicTo(.39f,-.91f,.34f,-.23f,-.38f,-.05f);
  mark.quadraticTo(-.69f,.03f,-.91f,-.10f);
  mark.moveTo(.25f,.4f);
  mark.quadraticTo(1.14f,-.56f,.95f,-.76f);
  mark.quadraticTo(.77f,-.91f,.37f,.2f);
  mark.quadraticTo(.14f,.85f,.75f,.37f);
  mark.quadraticTo(1.27f,-.27f,1.56f,.03f);
  mark.quadraticTo(1.19f,.67f,.9f,.59f);
  mark.quadraticTo(.67f,.5f,.94f,.16f);
  mark.moveTo(1.59f,-.02f);
  mark.quadraticTo(1.02f,.96f,1.83f,.38f);
  mark.quadraticTo(2.13f,-.28f,2.3f,.01f);
  mark.quadraticTo(1.85f,.91f,2.54f,.38f);
  mark.lineTo(2.76f,-.01f);
  downstrokes.moveTo(2.76f,-.01f);
  downstrokes.quadraticTo(2.27f,1.26f,1.83f,1.14f);
  mark.moveTo(1.83f,1.14f);
  mark.quadraticTo(1.57f,.91f,2.52f,.55f);
  mark.quadraticTo(3.45f,.21f,3.32f,-.02f);
  mark.quadraticTo(3.11f,-.22f,2.88f,.21f);
  mark.quadraticTo(2.65f,.78f,3.21f,.54f);
  mark.quadraticTo(3.54f,.39f,3.73f,-.03f);
  mark.lineTo(3.49f,.56f);
  mark.quadraticTo(3.85f,-.08f,4.07f,.02f);
  mark.quadraticTo(3.64f,.71f,4.25f,.51f);
  mark.cubicTo(4.57f,.45f,4.90f,.19f,5.32f,-.02f);
  return new ReelMark(mark,downstrokes,stroke(.076f),stroke(.125f),
      reelInk(0xb01f1511),reelInk(0xb0fff2df));
 }
 private static Brush reelInk(int argb)throws Exception{
  return (Brush)SWEEP.invoke(Brush.Companion,gradientStops(
      new float[]{0,1},new int[]{argb,argb}),pack(0,0));
 }
 private static void printReelMark(DrawScope scope,Canvas canvas,ReelMark mark,
      float x,float y,float scale,boolean lightSurface)throws Exception{
  // Keep the fallback legible when the host image backend is unavailable.
  canvas.save();
  try{
   canvas.translate(x,y);
   canvas.scale(scale,scale);
   Brush ink=lightSurface?mark.darkInk():mark.lightInk();
   BRUSH_PATH.invoke(scope,mark.hairlines(),ink,1f,mark.fine(),null,3);
   BRUSH_PATH.invoke(scope,mark.downstrokes(),ink,1f,mark.broad(),null,3);
  }finally{canvas.restore();}
 }
 private static kotlin.Pair<?,?>[] gradientStops(float[] offsets,int[] colors)throws Exception{
  kotlin.Pair<?,?>[] stops=new kotlin.Pair<?,?>[offsets.length];
  for(int i=0;i<offsets.length;i++)
   stops[i]=new kotlin.Pair<>(offsets[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  return stops;
 }
 static void bakeSolidReel(DrawScope fixed,DrawScope rotating,float radius,Supplier<Path> paths,
     DiscSettings.ReelColor color)throws Exception{
  var finish=buildReelFinish(radius,paths,color);
  var base=fixed.getDrawContext().getCanvas();
  base.save();
  try{
   base.translate(radius,radius);
   clipReel(base,finish);
   paintSolidReelSurface(fixed,base,radius,finish,0);
   paintReelMetal(fixed,base,radius,finish,0,true);
  }finally{base.restore();}
  var overlay=rotating.getDrawContext().getCanvas();
  overlay.save();
  try{
   overlay.translate(radius,radius);
   clipReel(overlay,finish);
   // The windows never intersect the outer rim or the center collar.
   paintReelWindows(rotating,overlay,radius,finish,null);
   paintReelFaceMark(rotating,overlay,radius,finish,color);
   paintReelHubMark(rotating,overlay,radius,finish,true);
  }finally{overlay.restore();}
 }
 static void bakeReelTape(DrawScope scope,float radius,Supplier<Path> paths)throws Exception{
  var tape=buildReelTape(radius,paths);
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate(radius,radius);
   paintReelTape(scope,radius,tape);
  }finally{canvas.restore();}
 }
 private static void paintReelTape(DrawScope scope,float radius,ReelTape tape)throws Exception{
  long center=pack(0,0);
  BRUSH_CIRCLE.invoke(scope,tape.body(),radius,center,1f,Fill.INSTANCE,null,3);
  BRUSH_PATH.invoke(scope,tape.grooves(),tape.grooveInk(),1f,tape.line(),null,3);
  BRUSH_PATH.invoke(scope,tape.edges(),tape.edgeInk(),1f,tape.line(),null,3);
  BRUSH_CIRCLE.invoke(scope,tape.sheen(),radius,center,1f,Fill.INSTANCE,null,3);
 }
 private static void clipReel(Canvas canvas,ReelFinish reel)throws Exception{
  CLIP.invoke(canvas,reel.outer(),1);
  CLIP.invoke(canvas,reel.spindle(),0);
 }
 private static void paintReelWindows(DrawScope scope,Canvas canvas,float radius,ReelFinish reel,Disc disc)throws Exception{
  canvas.save();
  try{
   CLIP.invoke(canvas,reel.windows(),1);
   if(disc==null||!disc.drawReelTape(scope,radius))paintReelTape(scope,radius,reel.tape());
   // The clip keeps the shaded lip inside the windows, clear of the cover artwork.
   BRUSH_PATH.invoke(scope,reel.windows(),reel.windowShade(),1f,reel.windowLip(),null,3);
   // Offset the strokes in opposite directions, inside the cutout, to reveal its recessed wall.
   canvas.save();
   try{
    canvas.translate(radius*.007f,radius*.021f);
    BRUSH_PATH.invoke(scope,reel.windows(),reel.windowSoftShadow(),1f,reel.windowLip(),null,3);
    canvas.translate(-radius*.004f,-radius*.009f);
    BRUSH_PATH.invoke(scope,reel.windows(),reel.windowContactShadow(),1f,reel.rim(),null,3);
    canvas.translate(-radius*.006f,-radius*.018f);
    BRUSH_PATH.invoke(scope,reel.windows(),reel.windowHighlight(),1f,reel.rim(),null,3);
   }finally{canvas.restore();}
  }finally{canvas.restore();}
 }
 private static void paintSolidReelSurface(DrawScope scope,Canvas canvas,float radius,ReelFinish reel,float angle)throws Exception{
  canvas.save();
  try{
   if(angle!=0f)canvas.rotate(-angle);
   BRUSH_CIRCLE.invoke(scope,reel.solidFace(),radius,pack(0,0),1f,Fill.INSTANCE,null,3);
  }finally{canvas.restore();}
 }
 private static void paintReelFaceMark(DrawScope scope,Canvas canvas,float radius,ReelFinish reel,
     DiscSettings.ReelColor color)throws Exception{
  float markX=radius*.008f,markY=-radius*.52f,markScale=radius*.040f;
  boolean lightFace=color!=DiscSettings.ReelColor.GRAPHITE&&color!=DiscSettings.ReelColor.WINE;
  if(!ReelWordmark.drawFace(scope,markX,markY,markScale*11.1f,lightFace))
   printReelMark(scope,canvas,reel.mark(),markX,markY,markScale,lightFace);
 }
 private static void paintReelMetal(DrawScope scope,Canvas canvas,float radius,ReelFinish reel,float angle,boolean solid)throws Exception{
  long center=pack(0,0);
  // Paint the dark inner seam beneath the flange on both reel styles.
  circle(scope,0xff292722,radius*.980f,center,reel.flangeSeam());
  canvas.save();
  try{
   if(angle!=0f)canvas.rotate(-angle);
   BRUSH_CIRCLE.invoke(scope,reel.flangeMetal(),radius*.984f,center,1f,reel.flange(),null,3);
   // Keep the lighting fixed while the artwork, windows and inscriptions turn.
   BRUSH_CIRCLE.invoke(scope,reel.flangeBevel(),radius*.994f,center,1f,reel.rimHighlight(),null,3);
   if(solid){
    circle(scope,0x64100d0d,radius*.355f,pack(0,radius*.009f),Fill.INSTANCE);
    BRUSH_CIRCLE.invoke(scope,reel.solidTrim(),radius*.345f,center,1f,Fill.INSTANCE,null,3);
    circle(scope,0xff232326,radius*.317f,center,Fill.INSTANCE);
   }else{
    BRUSH_CIRCLE.invoke(scope,reel.hubGlass(),radius*.338f,center,1f,Fill.INSTANCE,null,3);
    BRUSH_CIRCLE.invoke(scope,reel.glassReflection(),radius*.298f,center,1f,reel.reflectionWidth(),null,3);
    BRUSH_CIRCLE.invoke(scope,reel.glassBevel(),radius*.328f,center,1f,reel.collarBevel(),null,3);
    BRUSH_CIRCLE.invoke(scope,reel.glassInnerEdge(),radius*.316f,center,1f,reel.tape().line(),null,3);
    circle(scope,0x856d431e,radius*.340f,center,reel.tape().line());
    // A small contact shadow separates the raised collar from the transparent glass.
    circle(scope,0x36101317,radius*.205f,pack(radius*.005f,radius*.010f),Fill.INSTANCE);
    circle(scope,0xff22262a,radius*ReelGeometry.HUB_RADIUS,center,Fill.INSTANCE);
    BRUSH_CIRCLE.invoke(scope,reel.hubBevel(),radius*.193f,center,1f,Fill.INSTANCE,null,3);
    BRUSH_CIRCLE.invoke(scope,reel.hubRecess(),radius*.176f,center,1f,Fill.INSTANCE,null,3);
    circle(scope,0xff111316,radius*.098f,center,Fill.INSTANCE);
    BRUSH_CIRCLE.invoke(scope,reel.spindleBevel(),radius*.091f,center,1f,Fill.INSTANCE,null,3);
    circle(scope,0xff101215,radius*.077f,center,Fill.INSTANCE);
   }
  }finally{canvas.restore();}
 }
 private static void paintReelHubMark(DrawScope scope,Canvas canvas,float radius,ReelFinish reel,boolean solid)throws Exception{
  if(!ReelWordmark.drawHub(scope,solid?radius*1.74f:radius))
   printReelMark(scope,canvas,reel.mark(),radius*.015f,radius*.125f,radius*.013f,false);
 }
 private static void reelFace(DrawScope scope,float cx,float cy,float radius,ReelFinish reel,Source artwork,float aspect,
     float angle,DiscSettings.ReelColor color,Disc disc)throws Exception{
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate(cx,cy);
   clipReel(canvas,reel);
   paintReelWindows(scope,canvas,radius,reel,disc);
   canvas.save();
   try{
    CLIP.invoke(canvas,reel.windows(),0);
    if(artwork==null){
     paintSolidReelSurface(scope,canvas,radius,reel,angle);
     paintReelFaceMark(scope,canvas,radius,reel,color);
    }else{
     CLIP.invoke(canvas,reel.artworkFace(),1);
     var framed=artwork.discArtwork();
     boolean fit=framed!=null&&framed.framing().fit();
     circle(scope,fit?REEL_FIT_COLOR:0xffff4208,radius,pack(0,0),Fill.INSTANCE);
     if(!artwork.blankDisc())drawDiscArtwork(scope,artwork,framed,aspect,0,0,radius*2);
    }
   }finally{canvas.restore();}
   boolean solid=artwork==null;
   paintReelMetal(scope,canvas,radius,reel,angle,solid);
   paintReelHubMark(scope,canvas,radius,reel,solid);
  }finally{canvas.restore();}
 }
 private static Stroke stroke(float width)throws Exception{return STROKE.newInstance(width,4f,0,0,null,null);}
 private static Brush silver(float r)throws Exception{
  return (Brush)RADIAL.invoke(Brush.Companion,gradientStops(
      new float[]{0,1},new int[]{0xfff3f5f5,0xffbdc8cf}),pack(0,0),r,0);
 }
 private static Brush rim(float r)throws Exception{
  return (Brush)LINEAR.invoke(Brush.Companion,gradientStops(
      new float[]{0,.19f,.48f,.73f,1},
      new int[]{0xa6a6b2bb,0xdaf7f8f8,0xa5c9d3d8,0xc9f4f1eb,0xad9daab3}),
      pack(-r*.75f,-r*.65f),pack(r*.75f,r*.65f),0);
 }
 private static Brush hub(float r)throws Exception{
  // The transparent plastic and its two molded lips share the original radii.
  return (Brush)RADIAL.invoke(Brush.Companion,gradientStops(
      new float[]{0,.568f,.580f,.603f,.628f,.66f,.72f,.82f,.90f,.930f,.948f,.960f,.976f,.987f,1},
      new int[]{0x00ffffff,0xabedf1f0,0xd2fafbfa,0xb3e9eeed,0x9ea0abb1,0x8cdee4e4,0x75e1e6e5,0x70d8dfe2,
          0x88cfd9df,0xb3667782,0xe3ebf0f1,0xd94d5b67,0xece4ebee,0xd2f7f9f8,0x815c6971}),pack(0,0),r*.22f,0);
 }
 private static Brush metal(float r)throws Exception{
  return (Brush)LINEAR.invoke(Brush.Companion,gradientStops(
      new float[]{0,.18f,.34f,.46f,.54f,.64f,.80f,1},
      new int[]{0xa3485661,0xd9f0f5f7,0xa18e9fac,0xbec7bfae,0xe3fcfaf5,0xa557636c,0xd2e8eff2,0x9c616d76}),
      pack(-r*.18f,-r*.13f),pack(r*.18f,r*.13f),0);
 }
 private static Brush innerLip(float r)throws Exception{
  return (Brush)LINEAR.invoke(Brush.Companion,gradientStops(
      new float[]{0,.3f,.55f,.78f,1},new int[]{0x4a5c6970,0x3bc4ced0,0x64eef1ef,0xa4fafbf8,0x82e4eae8}),
      pack(-r*.10f,-r*.10f),pack(r*.10f,r*.10f),0);
 }
 private static Finish discFinish(float r,Finishes finishes)throws Exception{
  Finish style=finishes.cd;
  if(style==null||style.radius()!=r){
   float line=Math.max(.6f,Math.min(1.2f,r*.004f));
   finishes.cd=style=new Finish(r,stroke(line),stroke(Math.max(1f,Math.min(3.4f,r*.0115f))),
       stroke(Math.max(1.2f,Math.min(5f,r*.018f))),silver(r),rim(r),hub(r),metal(r),innerLip(r),stroke(r*.005f),stroke(r*.012f));
  }
  return style;
 }
 private static void paintCoverCdRim(DrawScope scope,Canvas canvas,float radius,Finish style,Finishes finishes)throws Exception{
  float line=style.line().getWidth(),edge=style.coverEdge().getWidth();
  var nativeCanvas=canvas instanceof SkiaBackedCanvas?SkiaBackedCanvas_skikoKt.getNativeCanvas(canvas):null;
  var paint=nativeCanvas==null?null:finishes.cdRimPaint();
  rimCircle(scope,nativeCanvas,paint,0xeefafcfd,radius-edge*.5f,style.coverEdge());
  rimCircle(scope,nativeCanvas,paint,0xafffffff,radius-line*.5f,style.line());
  rimCircle(scope,nativeCanvas,paint,0x236b7480,Math.max(0,radius-edge-line*.3f),style.line());
 }
 private static void rimCircle(DrawScope scope,org.jetbrains.skia.Canvas canvas,org.jetbrains.skia.Paint paint,
     int color,float radius,Stroke stroke)throws Exception{
  if(canvas==null){circle(scope,color,radius,pack(0,0),stroke);return;}
  paint.setColor(color);
  paint.setStrokeWidth(stroke.getWidth());
  canvas.drawCircle(0,0,radius,paint);
 }
 private static void paintBlankCdRim(DrawScope scope,float radius,Finish style)throws Exception{
  long center=pack(0,0);
  float line=style.line().getWidth(),edge=style.blankEdge().getWidth();
  BRUSH_CIRCLE.invoke(scope,style.rim(),radius-edge*.5f,center,1f,style.blankEdge(),null,3);
  circle(scope,0x63ffffff,radius-line*.5f,center,style.line());
  circle(scope,0x306b7480,Math.max(0,radius-edge-line*.3f),center,style.line());
 }
 private static void paintCdHub(DrawScope scope,Canvas canvas,float r,Finish style)throws Exception{
  long center=pack(0,0);
  BRUSH_CIRCLE.invoke(scope,style.hub(),r*.22f,center,1f,Fill.INSTANCE,null,3);
  BRUSH_CIRCLE.invoke(scope,style.metal(),r*.21f,center,1f,style.bevel(),null,3);
  // Reverse the adjacent polished lip so bright and dark edges separate.
  canvas.rotate(180f);
  BRUSH_CIRCLE.invoke(scope,style.metal(),r*.216f,center,1f,style.bevel(),null,3);
  canvas.rotate(-180f);
  BRUSH_CIRCLE.invoke(scope,style.innerLip(),r*.134f,center,1f,style.lip(),null,3);
 }
 private static void circle(DrawScope scope,int color,float radius,long center,DrawStyle style)throws Exception{
  CIRCLE.invoke(scope,ColorKt.Color(color),radius,center,1f,style,null,3);
 }
 private static void cover(DrawScope scope,Source source,Supplier<Path> paths,float side,float width,float height,
     CoverLayer coverLayer,float radius)throws Exception{
  // Fit the complete sleeve and clip only its visible artwork. Copies of
  // the artwork used to generate the disc keep their original pixels.
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{
   canvas.translate((side-width)*.5f,(side-height)*.5f);
   if(coverLayer!=null){
    coverLayer.applyTilt(canvas,width,height);
    if(radius>0)CLIP.invoke(canvas,coverLayer.outline(paths,width,height,radius),1);
   }
   source.draw(scope,width,height);
  }finally{canvas.restore();}
 }
 static long pack(float x,float y){return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL);}
 static float x(long p){return Float.intBitsToFloat((int)(p>>>32));}
 static float y(long p){return Float.intBitsToFloat((int)p);}
}
