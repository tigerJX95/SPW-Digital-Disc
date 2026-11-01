package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.graphics.Canvas;
import androidx.compose.ui.graphics.Paint;
import androidx.compose.ui.graphics.SkiaBackedPaint_skikoKt;
import java.util.function.Supplier;
/** Uniform whole-disc opacity. One cached paint per presentation, no moving mask. */
final class FrontDiscLayer {
 private final Supplier<Paint> paints;
 private Paint paint;
 FrontDiscLayer(){this(SkiaBackedPaint_skikoKt::Paint);}
 FrontDiscLayer(Supplier<Paint> paints){this.paints=paints;}
 static float opacity(float progress){
  float t=Math.max(0,Math.min(1,progress/.2f));return t*t*(3-2*t);
 }
 // Caller restores the layer only when true is returned. Fully open discs do
 // not need an offscreen layer; blending is limited to entry/exit near zero.
 boolean begin(Canvas canvas,float width,float side,float progress){
  float alpha=opacity(progress);if(alpha>=1)return false;
  if(paint==null)paint=paints.get();paint.setAlpha(alpha);
  canvas.saveLayer(new Rect(0,0,width,side),paint);return true;
 }
}
