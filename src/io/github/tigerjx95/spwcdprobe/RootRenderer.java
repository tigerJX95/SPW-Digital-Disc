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
 interface Source {
  void draw(DrawScope scope,float width,float height)throws Exception;
  default boolean blankDisc(){return false;}
 }
 interface Disc {boolean draw(DrawScope scope,float cx,float cy,float diameter)throws Exception;}
 private static final Method CLIP,CLIP_RECT,CIRCLE,BRUSH_CIRCLE,RADIAL,LINEAR,BOX_COLOR,SIZE,INTRINSIC,DRAW;
 private static final Constructor<Stroke> STROKE;
 private record Finish(float radius,Stroke line,Stroke edge,Brush hub,Brush metal,Brush innerLip,Stroke bevel,Stroke lip){}
 private static volatile Finish cachedFinish;
 private record VinylFinish(float radius,DiscSettings.VinylColor color,Path grooves,Path label,Path hole,Path spindleInset,Stroke grooveStroke,Stroke rim,Brush surface,Brush groovesLight,Brush falloff){}
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
  long intrinsic=(Long)INTRINSIC.invoke(painter);float iw=x(intrinsic),ih=y(intrinsic);
  if(!Float.isFinite(iw)||!Float.isFinite(ih)||iw<=0||ih<=0){iw=ih=1;}
  final float ratio=iw/ih;
  paint(scope,ratio,new Source(){
   public void draw(DrawScope s,float w,float h)throws Exception{enter.run();try{DRAW.invoke(painter,s,pack(w,h),1f,null);}finally{leave.run();}}
   public boolean blankDisc(){return blank;}
  },disc,SkiaBackedPath_skikoKt::Path,exposure,amount,front,layer,angle,style,color,false,reelColor);
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
  float radius=(trimmed?Math.min(side,fittedHeight(side,aspect)):side)*.49f;
  if(style.reel()){
   reelFinish(radius,paths,reelColor);
   ReelWordmark.prepare(style==DiscSettings.Style.REEL);
  }else if(style!=DiscSettings.Style.CD){
   vinylFinish(radius,paths,vinylColor);
  }else discFinish(radius);
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
  exposure=Math.max(0,Math.min(1,exposure));
  long size=(Long)SIZE.invoke(scope);float w=x(size),h=y(size),side=Math.min(h,w/DiscExposure.widthFactor(amount));
  if(side<=0||!Float.isFinite(side))return;
  // The complete group fits its independently measured width at every amount.
  float coverWidth=fittedWidth(side,aspect),coverHeight=fittedHeight(side,aspect);
  float right=fitTrimmed?(side+coverWidth)*.5f:side;
  float discSide=fitTrimmed?Math.min(side,coverHeight):side;
  float r=discSide*.49f,cy=side*.5f;
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
  if(front)cover(scope,aspect,source,side);
  Canvas moving=scope.getDrawContext().getCanvas();moving.save();
  try{
   // Front discs stay whole throughout their motion. Only the fixed Canvas
   // bounds clip them; there is no right-to-left reveal edge over the sleeve.
   CLIP_RECT.invoke(moving,front?0f:right,0f,w,side,1);
   if(exposure>0&&DiscExposure.fraction(amount)>0){
   boolean blended=front&&layer.begin(moving,w,side,exposure);
   moving.save();
   try{
    if(angle!=0f){moving.translate(cx,cy);moving.rotate(angle);moving.translate(-cx,-cy);}
  if(!disc.draw(scope,cx,cy,r*2f)){
   if(style.reel())reelFace(scope,cx,cy,r,paths,style==DiscSettings.Style.REEL_COVER?source:null,aspect,angle,reelColor);
   else if(style!=DiscSettings.Style.CD)vinylFace(scope,source,aspect,cx,cy,r,paths,angle,color);
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
 private static VinylFinish vinylFinish(float radius,Supplier<Path> paths,DiscSettings.VinylColor color)throws Exception{
  VinylFinish finish=cachedVinyl;
  if(finish!=null&&finish.radius()==radius&&finish.color()==color)return finish;
  boolean colored=color!=DiscSettings.VinylColor.BLACK;
  Path grooves=paths.get();
  if(colored){
   // Wider gaps separate bands of closely spaced grooves from the smooth runout.
   float[] starts={.480f,.575f,.691f,.818f,.913f};
   float[] spacing={.011f,.012f,.013f,.010f,.011f};
   int[] counts={8,8,8,8,6};
   for(int band=0;band<starts.length;band++){
    for(int ring=0;ring<counts[band];ring++){
     float r=radius*(starts[band]+ring*spacing[band]);
     grooves.addOval(new Rect(-r,-r,r,r),Path.Direction.Clockwise);
    }
   }
  }else{
   for(int ring=0;ring<42;ring++){
    float r=radius*(.395f+ring*.0141f);
    grooves.addOval(new Rect(-r,-r,r,r),Path.Direction.Clockwise);
   }
  }
  float[] angles={0,1/12f,1/6f,1/4f,1/3f,5/12f,.5f,7/12f,2/3f,.75f,5/6f,11/12f,1};
  Brush surface=(Brush)SWEEP.invoke(Brush.Companion,vinylStops(color,angles,
      colored?new int[]{72,79,83,80,77,73,70,78,85,83,79,75,72}
             :new int[]{12,21,40,63,40,21,11,19,37,60,38,21,12},colored?0x60:0xff,false),pack(0,0));
  float[] glints={0,.06f,.12f,.17f,.24f,.52f,.57f,.62f,.67f,.74f,1};
  Brush groovesLight=(Brush)SWEEP.invoke(Brush.Companion,
      colored?vinylStops(color,glints,new int[]{13,19,39,26,15,13,23,47,32,15,13},0x74,true)
             :vinylStops(color,angles,new int[]{18,28,52,85,52,28,17,25,49,81,50,28,18},0xff,false),pack(0,0));
  float[] stops=colored?new float[]{0,.375f,.42f,.45f,.47f,.65f,.89f,.97f,1}
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
                                 0x33000000|runout,0x03000000|runout,0x0a000000,0x00000000,0x12000000,0x21000000}
                      :new int[]{0x00000000,0x00000000,0x00000000,0x12000000,0x27000000,0x45000000,0x45000000};
  kotlin.Pair<?,?>[] shading=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++)shading[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  Brush falloff=(Brush)RADIAL.invoke(Brush.Companion,shading,pack(0,0),radius,0);
  return cachedVinyl=new VinylFinish(radius,color,grooves,oval(paths,radius*.375f),oval(paths,radius*.029f),oval(paths,radius*.038f),
      stroke(radius*(colored?.0037f:.0065f)),stroke(radius*.018f),surface,groovesLight,falloff);
 }
 private static kotlin.Pair<?,?>[] vinylStops(DiscSettings.VinylColor color,float[] stops,int[] levels,int alpha,boolean glints)throws Exception{
  kotlin.Pair<?,?>[] values=new kotlin.Pair<?,?>[stops.length];
  for(int i=0;i<stops.length;i++){
   int argb=glints?((levels[i]*alpha/85)<<24)|0x00ffffff:(alpha<<24)|(color.shade(levels[i])&0x00ffffff);
   values[i]=new kotlin.Pair<>(stops[i],BOX_COLOR.invoke(null,ColorKt.Color(argb)));
  }
  return values;
 }
 private static void vinylFace(DrawScope scope,Source source,float aspect,float cx,float cy,float radius,Supplier<Path> paths,float angle,DiscSettings.VinylColor color)throws Exception{
  VinylFinish finish=vinylFinish(radius,paths,color);
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
   boolean classic=color==DiscSettings.VinylColor.BLACK;
   int rimColor=classic?color.shade(54):0xa0000000|(color.shade(70)&0xffffff);
   circle(scope,rimColor,radius-finish.rim().getWidth()*.5f,center,finish.rim());
   if(classic){
    CIRCLE.invoke(scope,ColorKt.Color(0xff101010),radius*.386f,center,1f,Fill.INSTANCE,null,3);
    CIRCLE.invoke(scope,ColorKt.Color(0xff252525),radius*.375f,center,1f,Fill.INSTANCE,null,3);
   }
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
 private record ReelFinish(float radius,DiscSettings.ReelColor color,Path outer,Path artworkFace,Path windows,Path spindle,Stroke rim,
      Stroke tapeLine,Brush tape,Path tapeGrooves,Path tapeEdges,Brush tapeGrooveInk,Brush tapeEdgeInk,Brush hubGlass,Brush glassReflection,Brush glassInnerEdge,Brush tapeSheen,Stroke windowLip,Brush windowShade,
      Brush windowSoftShadow,Brush windowContactShadow,Brush windowHighlight,ReelMark mark,
      Stroke flange,Stroke flangeSeam,Brush flangeMetal,Brush flangeBevel,Brush hubBevel,Brush hubRecess,Brush spindleBevel,
      Brush solidFace,Brush solidTrim,Brush glassBevel,Stroke collarBevel,Stroke reflectionWidth){}
 private static volatile ReelFinish cachedReel;
 private static ReelFinish reelFinish(float radius,Supplier<Path> paths,DiscSettings.ReelColor color)throws Exception{
  ReelFinish finish=cachedReel;
  if(finish!=null&&finish.radius()==radius&&finish.color()==color)return finish;
  finish=buildReelFinish(radius,paths,color);
  cachedReel=finish;
  return finish;
 }
 private static ReelFinish buildReelFinish(float radius,Supplier<Path> paths,DiscSettings.ReelColor color)throws Exception{
  Path windows=paths.get();
  float outer=radius*.866f,inner=radius*.435f;
  for(int middle=90;middle<360;middle+=120){
   float start=middle-13, end=middle+13;
   reelPoint(windows,outer,start+2,true);
   windows.arcTo(new Rect(-outer,-outer,outer,outer),start+2,22,false);
   reelCorner(windows,outer,end,outer-radius*.018f,end);
   reelPoint(windows,inner+radius*.018f,end,false);
   reelCorner(windows,inner,end,inner,end-3);
   windows.arcTo(new Rect(-inner,-inner,inner,inner),end-3,-20,false);
   reelCorner(windows,inner,start,inner+radius*.018f,start);
   reelPoint(windows,outer-radius*.018f,start,false);
   reelCorner(windows,outer,start,outer,start+2);
   windows.close();
  }
  Brush tape=(Brush)RADIAL.invoke(Brush.Companion,reelStops(
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
  Brush solidFace=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.075f,.19f,.315f,.44f,.565f,.69f,.815f,.94f,1},
      new int[]{tone(color.rgb,0),tone(color.rgb,color.sheen),tone(color.rgb,-color.sheen/2),
                tone(color.rgb,color.sheen),tone(color.rgb,-color.sheen/2),tone(color.rgb,color.sheen),
                tone(color.rgb,-color.sheen/2),tone(color.rgb,color.sheen),tone(color.rgb,-color.sheen/2),tone(color.rgb,0)}),pack(0,0));
  Brush hubGlass=(Brush)RADIAL.invoke(Brush.Companion,reelStops(
      new float[]{0,.63f,.77f,.88f,.94f,1},
      new int[]{0x1015222c,0x1215222c,0x0c1d2932,0x102b3944,0x38202e37,0x58202e37}),
      pack(0,0),radius*.338f,0);
  Brush glassReflection=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.05f,.11f,.17f,.25f,.49f,.55f,.62f,.70f,.81f,1},
      new int[]{0x00f5fbff,0x00f5fbff,0x28f5fbff,0x06f5fbff,0x00f5fbff,0x00f5fbff,
                0x28f5fbff,0xa0f5fbff,0x1af5fbff,0x00f5fbff,0x00f5fbff}),pack(0,0));
  Brush glassInnerEdge=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.14f,.29f,.50f,.63f,.79f,1},
      new int[]{0x1af5fbff,0x54f5fbff,0x29f5fbff,0x24232f38,0xa2f5fbff,0x20f5fbff,0x1af5fbff}),pack(0,0));
  Brush glassBevel=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.18f,.38f,.50f,.62f,.72f,.85f,1},
      new int[]{0xff494e52,0xff373b3f,0xff4a4e52,0xff646b70,0xffacb1b5,0xff666d73,0xff454a4f,0xff494e52}),
      pack(0,0));
  Brush tapeSheen=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.12f,.23f,.30f,.42f,.54f,.65f,.74f,.88f,1},
      new int[]{0x04e8b78f,0x2de8b78f,0x04e8b78f,0x260b0806,0x27e8b78f,
                0x04e8b78f,0x220b0806,0x22e8b78f,0x1f0b0806,0x04e8b78f}),
      pack(0,0));
  Brush windowShade=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.22f,.39f,.57f,.72f,1},
      new int[]{0x9a140d09,0x521b130e,0xa4140d09,0x511b130e,0xa1140d09,0x9a140d09}),
      pack(0,0));
  Brush flangeMetal=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.19f,.36f,.50f,.62f,.71f,.83f,1},
      new int[]{0xff555652,0xff434440,0xff646460,0xff676762,0xffb3afa5,0xff92918a,0xff6d6e69,0xff555652}),
      pack(0,0));
  Brush flangeBevel=(Brush)SWEEP.invoke(Brush.Companion,reelStops(
      new float[]{0,.24f,.48f,.62f,.76f,1},
      new int[]{0xff777873,0xff585955,0xff83847d,0xffdad4c7,0xffb1afa5,0xff777873}),
      pack(0,0));
  float[] metalStops={0,.13f,.32f,.48f,.62f,.74f,.86f,1};
  Brush hubBevel=(Brush)SWEEP.invoke(Brush.Companion,reelStops(metalStops,
      new int[]{0xff626970,0xff3a3e44,0xff52575c,0xff7a8085,0xffb2b9be,0xff8f979d,0xff747b82,0xff626970}),pack(0,0));
  Brush hubRecess=(Brush)RADIAL.invoke(Brush.Companion,reelStops(
      new float[]{0,.55f,.82f,.95f,1},
      new int[]{0xff25272a,0xff27292c,0xff222427,0xff191b1e,0xff101215}),pack(0,0),radius*.176f,0);
  Brush spindleBevel=(Brush)SWEEP.invoke(Brush.Companion,reelStops(metalStops,
      new int[]{0xff565b60,0xff92989c,0xff73797d,0xff4d5359,0xff34393f,0xff575f65,0xff687076,0xff565b60}),pack(0,0));
  Brush solidTrim=(Brush)SWEEP.invoke(Brush.Companion,reelStops(metalStops,
      new int[]{tone(color.trim,8),tone(color.trim,-26),tone(color.trim,-12),tone(color.trim,22),
                tone(color.trim,48),tone(color.trim,32),tone(color.trim,-14),tone(color.trim,8)}),pack(0,0));
  // The face overlaps the metal's inner edge (.970r) but stops beneath the opaque flange.
  return new ReelFinish(radius,color,oval(paths,radius),oval(paths,radius*.985f),windows,oval(paths,radius*.057f),
      stroke(radius*.009f),stroke(radius*.0024f),tape,tapeGrooves,tapeEdges,reelInk(0x76140c08),reelInk(0x46c99469),hubGlass,glassReflection,glassInnerEdge,tapeSheen,stroke(radius*.036f),windowShade,
      reelInk(0x80100b08),reelInk(0xae080503),reelInk(0x94d7c7b2),reelMark(paths),
      stroke(radius*.028f),stroke(radius*.038f),flangeMetal,flangeBevel,hubBevel,hubRecess,spindleBevel,solidFace,solidTrim,glassBevel,
      stroke(radius*.020f),stroke(radius*.035f));
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
  return (Brush)SWEEP.invoke(Brush.Companion,reelStops(
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
 private static void reelPoint(Path path,float radius,float degrees,boolean move){
  double angle=Math.toRadians(degrees);
  float x=radius*(float)Math.cos(angle),y=radius*(float)Math.sin(angle);
  if(move)path.moveTo(x,y);else path.lineTo(x,y);
 }
 private static void reelCorner(Path path,float radius,float degrees,float nextRadius,float nextDegrees){
  double corner=Math.toRadians(degrees),next=Math.toRadians(nextDegrees);
  path.quadraticTo(radius*(float)Math.cos(corner),radius*(float)Math.sin(corner),
      nextRadius*(float)Math.cos(next),nextRadius*(float)Math.sin(next));
 }
 private static kotlin.Pair<?,?>[] reelStops(float[] offsets,int[] colors)throws Exception{
  kotlin.Pair<?,?>[] stops=new kotlin.Pair<?,?>[offsets.length];
  for(int i=0;i<offsets.length;i++)
   stops[i]=new kotlin.Pair<>(offsets[i],BOX_COLOR.invoke(null,ColorKt.Color(colors[i])));
  return stops;
 }
 private static void reelFace(DrawScope scope,float cx,float cy,float radius,Supplier<Path> paths,Source artwork,float aspect,float angle,DiscSettings.ReelColor color)throws Exception{
  reelFace(scope,cx,cy,radius,reelFinish(radius,paths,color),artwork,aspect,angle,color);
 }
 static void bakeSolidReel(DrawScope scope,float radius,Supplier<Path> paths,DiscSettings.ReelColor color)throws Exception{
  // Shader brushes retain per-canvas state. A worker owns a fresh finish.
  var finish=buildReelFinish(radius,paths,color);
  reelFace(scope,radius,radius,radius,finish,null,1,0,color);
 }
 private static void reelFace(DrawScope scope,float cx,float cy,float radius,ReelFinish reel,Source artwork,float aspect,float angle,DiscSettings.ReelColor color)throws Exception{
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  long center=pack(0,0);
  try{
   canvas.translate(cx,cy);
   CLIP.invoke(canvas,reel.outer(),1);
   CLIP.invoke(canvas,reel.spindle(),0);
   canvas.save();
   try{
    CLIP.invoke(canvas,reel.windows(),1);
    BRUSH_CIRCLE.invoke(scope,reel.tape(),radius,center,1f,Fill.INSTANCE,null,3);
    BRUSH_PATH.invoke(scope,reel.tapeGrooves(),reel.tapeGrooveInk(),1f,reel.tapeLine(),null,3);
    BRUSH_PATH.invoke(scope,reel.tapeEdges(),reel.tapeEdgeInk(),1f,reel.tapeLine(),null,3);
    BRUSH_CIRCLE.invoke(scope,reel.tapeSheen(),radius,center,1f,Fill.INSTANCE,null,3);
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
   canvas.save();
   try{
    CLIP.invoke(canvas,reel.windows(),0);
    if(artwork!=null)CLIP.invoke(canvas,reel.artworkFace(),1);
    if(artwork==null){
     canvas.save();
     try{
      if(angle!=0f)canvas.rotate(-angle);
      BRUSH_CIRCLE.invoke(scope,reel.solidFace(),radius,center,1f,Fill.INSTANCE,null,3);
     }finally{canvas.restore();}
    }else circle(scope,0xffff4208,radius,center,Fill.INSTANCE);
    if(artwork!=null&&!artwork.blankDisc()){
     canvas.save();
     try{
      float diameter=radius*2;
      float width=diameter*Math.max(1f,aspect),height=diameter*Math.max(1f,1f/aspect);
      canvas.translate(-width*.5f,-height*.5f);
      artwork.draw(scope,width,height);
     }finally{canvas.restore();}
    }
    if(artwork==null){
     float markX=radius*.008f,markY=-radius*.52f,markScale=radius*.040f;
     boolean lightFace=color!=DiscSettings.ReelColor.GRAPHITE&&color!=DiscSettings.ReelColor.WINE;
     if(!ReelWordmark.drawFace(scope,markX,markY,markScale*11.1f,lightFace))
      printReelMark(scope,canvas,reel.mark(),markX,markY,markScale,lightFace);
    }
   }finally{canvas.restore();}
   // Paint the dark inner seam beneath the flange on both reel styles.
   circle(scope,0xff292722,radius*.980f,center,reel.flangeSeam());
   canvas.save();
   try{
    if(angle!=0f)canvas.rotate(-angle);
    BRUSH_CIRCLE.invoke(scope,reel.flangeMetal(),radius*.984f,center,1f,reel.flange(),null,3);
    BRUSH_CIRCLE.invoke(scope,reel.flangeBevel(),radius*.994f,center,1f,reel.tapeLine(),null,3);
   }finally{canvas.restore();}
   boolean solid=artwork==null;
   canvas.save();
   try{
    // Keep the lighting fixed while the artwork, windows and inscriptions turn.
    if(angle!=0f)canvas.rotate(-angle);
    if(solid){
     circle(scope,0x64100d0d,radius*.355f,pack(0,radius*.009f),Fill.INSTANCE);
     BRUSH_CIRCLE.invoke(scope,reel.solidTrim(),radius*.345f,center,1f,Fill.INSTANCE,null,3);
     circle(scope,0xff232326,radius*.317f,center,Fill.INSTANCE);
    }else{
     BRUSH_CIRCLE.invoke(scope,reel.hubGlass(),radius*.338f,center,1f,Fill.INSTANCE,null,3);
     BRUSH_CIRCLE.invoke(scope,reel.glassReflection(),radius*.298f,center,1f,reel.reflectionWidth(),null,3);
     BRUSH_CIRCLE.invoke(scope,reel.glassBevel(),radius*.328f,center,1f,reel.collarBevel(),null,3);
     BRUSH_CIRCLE.invoke(scope,reel.glassInnerEdge(),radius*.316f,center,1f,reel.tapeLine(),null,3);
     circle(scope,0x856d431e,radius*.340f,center,reel.tapeLine());
     // A small contact shadow separates the raised collar from the transparent glass.
     circle(scope,0x36101317,radius*.205f,pack(radius*.005f,radius*.010f),Fill.INSTANCE);
     circle(scope,0xff22262a,radius*.199f,center,Fill.INSTANCE);
     BRUSH_CIRCLE.invoke(scope,reel.hubBevel(),radius*.193f,center,1f,Fill.INSTANCE,null,3);
     BRUSH_CIRCLE.invoke(scope,reel.hubRecess(),radius*.176f,center,1f,Fill.INSTANCE,null,3);
     circle(scope,0xff111316,radius*.098f,center,Fill.INSTANCE);
     BRUSH_CIRCLE.invoke(scope,reel.spindleBevel(),radius*.091f,center,1f,Fill.INSTANCE,null,3);
     circle(scope,0xff101215,radius*.077f,center,Fill.INSTANCE);
    }
   }finally{canvas.restore();}
   if(!ReelWordmark.drawHub(scope,solid?radius*1.74f:radius))
    printReelMark(scope,canvas,reel.mark(),radius*.015f,radius*.125f,radius*.013f,false);
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
 private static Finish discFinish(float r)throws Exception{
  Finish style=cachedFinish;
  if(style==null||style.radius()!=r){
   float line=Math.max(.6f,Math.min(1.2f,r*.004f));
   cachedFinish=style=new Finish(r,stroke(line),stroke(Math.max(1.2f,Math.min(5f,r*.018f))),hub(r),metal(r),innerLip(r),stroke(r*.005f),stroke(r*.012f));
  }
  return style;
 }
 private static void finish(DrawScope scope,float r,long center)throws Exception{
  Finish style=discFinish(r);
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
 private static void circle(DrawScope scope,int color,float radius,long center,DrawStyle style)throws Exception{
  CIRCLE.invoke(scope,ColorKt.Color(color),radius,center,1f,style,null,3);
 }
 private static void cover(DrawScope scope,float aspect,Source source,float side)throws Exception{
  // Complete source fitted proportionally into its own square, drawn over CD.
  float cw=fittedWidth(side,aspect),ch=fittedHeight(side,aspect);
  Canvas canvas=scope.getDrawContext().getCanvas();canvas.save();
  try{canvas.translate((side-cw)*.5f,(side-ch)*.5f);source.draw(scope,cw,ch);}finally{canvas.restore();}
 }
 static long pack(float x,float y){return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL);}
 static float x(long p){return Float.intBitsToFloat((int)(p>>>32));}
 static float y(long p){return Float.intBitsToFloat((int)p);}
}
