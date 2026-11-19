package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.geometry.Rect;

/** Use the native cover size while keeping the overlay's centered entry position. */
record CoverHandoff(float scale) {
 static final CoverHandoff FALLBACK=new CoverHandoff(DiscTransition.COVER_START_SCALE);

 static CoverHandoff from(Rect nativeBounds,float side,float aspect){
  if(nativeBounds==null)return FALLBACK;
  float scale=Math.min(nativeBounds.getWidth()/RootRenderer.fittedWidth(side,aspect),
      nativeBounds.getHeight()/RootRenderer.fittedHeight(side,aspect));
  if(!Float.isFinite(scale)||scale<=0)return FALLBACK;
  return new CoverHandoff(scale);
 }

 static float remaining(float animatedScale){
  return (1-animatedScale)/(1-DiscTransition.COVER_START_SCALE);
 }

 float animatedScale(float remaining){return 1+(scale-1)*remaining;}
}
