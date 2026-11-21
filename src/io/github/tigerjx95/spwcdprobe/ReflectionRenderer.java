package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.*;
import androidx.compose.ui.graphics.drawscope.*;
import androidx.compose.ui.geometry.Rect;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
/** Whole-group mirror in a bounded isolated layer. Same approach as the working SPW reference plugin. */
final class ReflectionRenderer {
 interface Content {void draw()throws Exception;}
 record Band(float edge,float gap,float height,float opacity) {
  float top(){return edge+gap;}
  float bottom(){return top()+height;}
  float sourceTop(){return edge-height;}
 }
 static Band returning(float side,float coverHeight,float height,float gap,float progress){
  // The native small cover mirrors from the edge and fades over half
  // its height. Match that profile before it becomes visible.
  float edge=side+(coverHeight-side)*.5f*progress;
  float fade=height+(coverHeight*.5f-height)*progress;
  return new Band(edge,gap*(1-progress),fade,.18f+(.15f-.18f)*progress);
 }
 private static final Method CLIP,GRADIENT,BOX_COLOR,RECT;
 static{
  try{
   CLIP=Canvas.class.getMethod("clipRect-N_I0leg",float.class,float.class,float.class,float.class,int.class);
   GRADIENT=Brush.Companion.getClass().getMethod("verticalGradient-8A-3gB4",List.class,float.class,float.class,int.class);
   BOX_COLOR=Color.class.getMethod("box-impl",long.class);
   RECT=DrawScope.class.getMethod("drawRect-AsUm42w",Brush.class,long.class,long.class,float.class,DrawStyle.class,ColorFilter.class,int.class);
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 private final Supplier<Paint> paints;
 private Paint layer;
 private float layerOpacity=Float.NaN;
 private Brush fade;
 private float fadeTop,fadeBottom;
 ReflectionRenderer(){this(SkiaBackedPaint_skikoKt::Paint);}
 ReflectionRenderer(Supplier<Paint> paints){this.paints=paints;}
 void draw(DrawScope scope,float side,float width,float height,float gap,Content content)throws Exception{
  draw(scope,width,new Band(side,gap,height,.18f),Float.POSITIVE_INFINITY,content);
 }
 void draw(DrawScope scope,float width,Band band,float clipBottom,Content content)throws Exception{
  float top=band.top(),end=band.bottom(),bottom=Math.min(end,clipBottom);
  if(band.edge()<=0||width<=0||bottom<=top)return;
  if(layer==null)layer=paints.get();
  if(layerOpacity!=band.opacity()){layer.setAlpha(band.opacity());layerOpacity=band.opacity();}
  if(fade==null||top!=fadeTop||end!=fadeBottom){
   fade=(Brush)GRADIENT.invoke(Brush.Companion,List.of(BOX_COLOR.invoke(null,ColorKt.Color(0xff000000)),BOX_COLOR.invoke(null,ColorKt.Color(0x00000000))),top,end,0);
   fadeTop=top;fadeBottom=end;
  }
  Canvas canvas=scope.getDrawContext().getCanvas();
  canvas.save();
  try{
   CLIP.invoke(canvas,0f,top,width,bottom,1);
   canvas.saveLayer(new Rect(0,top,width,bottom),layer);
   try{
    canvas.save();
    try{canvas.translate(0,band.edge()+top);canvas.scale(1,-1);content.draw();}finally{canvas.restore();}
    RECT.invoke(scope,fade,RootRenderer.pack(0,top),RootRenderer.pack(width,bottom-top),1f,Fill.INSTANCE,null,6); // DstIn
   }finally{canvas.restore();}
  }finally{canvas.restore();}
 }
}
