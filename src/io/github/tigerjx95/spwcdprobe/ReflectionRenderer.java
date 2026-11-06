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
 private Brush fade;
 private float fadeTop,fadeBottom;
 ReflectionRenderer(){this(SkiaBackedPaint_skikoKt::Paint);}
 ReflectionRenderer(Supplier<Paint> paints){this.paints=paints;}
 void draw(DrawScope scope,float side,float width,float height,float gap,Content content)throws Exception{
  if(side<=0||width<=0||height<=0)return;
  if(layer==null){layer=paints.get();layer.setAlpha(.18f);}
  float top=side+gap,bottom=top+height;
  if(fade==null||top!=fadeTop||bottom!=fadeBottom){
   fade=(Brush)GRADIENT.invoke(Brush.Companion,List.of(BOX_COLOR.invoke(null,ColorKt.Color(0xff000000)),BOX_COLOR.invoke(null,ColorKt.Color(0x00000000))),top,bottom,0);
   fadeTop=top;fadeBottom=bottom;
  }
  Canvas canvas=scope.getDrawContext().getCanvas();
  canvas.save();
  try{
   CLIP.invoke(canvas,0f,top,width,bottom,1);
   canvas.saveLayer(new Rect(0,top,width,bottom),layer);
   try{
    canvas.save();
    try{canvas.translate(0,2*side+gap);canvas.scale(1,-1);content.draw();}finally{canvas.restore();}
    RECT.invoke(scope,fade,RootRenderer.pack(0,top),RootRenderer.pack(width,height),1f,Fill.INSTANCE,null,6); // DstIn
   }finally{canvas.restore();}
  }finally{canvas.restore();}
 }
}
