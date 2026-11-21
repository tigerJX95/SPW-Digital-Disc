package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.Modifier;
import androidx.compose.foundation.layout.*;
import androidx.compose.foundation.ClickableKt;
import androidx.compose.ui.graphics.GraphicsLayerModifierKt;
import androidx.compose.ui.graphics.GraphicsLayerScope;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.node.NodeCoordinator;
import androidx.compose.ui.unit.*;
import java.lang.reflect.Method;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
/** Our sibling Canvas has real width/height and its own matching click rectangle. */
final class CanvasWidget {
 private static final Method SIZE,BOX,CLICK;
 static {
  try{
   SIZE=SizeKt.class.getMethod("requiredSize-VpY3zN4",Modifier.class,float.class,float.class);
   BOX=IntOffset.class.getMethod("box-impl",long.class);
   CLICK=ClickableKt.class.getMethod("clickable-O2vRcR0",Modifier.class,androidx.compose.foundation.interaction.MutableInteractionSource.class,androidx.compose.foundation.Indication.class,boolean.class,String.class,androidx.compose.ui.semantics.Role.class,Function0.class);
  }catch(Exception e){throw new ExceptionInInitializerError(e);}
 }
 static Modifier modifier(SelfDrawLayout plan,float density,Function0<Unit> click)throws Exception{
  return modifier(plan,density,click,layer->Unit.INSTANCE);
 }
 static Modifier modifier(SelfDrawLayout plan,float density,Function0<Unit> click,
     Function1<GraphicsLayerScope,Unit> layerTransform)throws Exception{
  Modifier offset=OffsetKt.absoluteOffset(Modifier.Companion,(Density d)->{
   try{return (IntOffset)BOX.invoke(null,((long)Math.round(plan.left())<<32)|(Math.round(plan.top())&0xffffffffL));}
   catch(Exception e){throw new IllegalStateException(e);}
  });
  Modifier sized=(Modifier)SIZE.invoke(null,offset,plan.width()/density,plan.height()/density);
  // Keep hit testing inside the transformed recording layer so the click target
  // moves with the cover and the disc.
  Modifier layer=GraphicsLayerModifierKt.graphicsLayer(sized,layerTransform);
  return (Modifier)CLICK.invoke(null,layer,null,null,true,null,null,click);
 }
 static boolean hasDrawingLayer(LayoutCoordinates coordinates){
  return coordinates instanceof NodeCoordinator coordinator&&coordinator.isAttached()
      &&coordinator.getLayoutNode().getInnerLayerCoordinator$ui()!=null;
 }
 static boolean requestDraw(LayoutCoordinates coordinates){
  if(!(coordinates instanceof NodeCoordinator coordinator)||!coordinator.isAttached())return false;
  coordinator.getLayoutNode().invalidateLayer$ui();
  return true;
 }
}
