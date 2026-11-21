package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.geometry.Rect;

/** Match the on-screen cover before the overlay enlarges and centers it. */
record CoverHandoff(float scale) {
 private static final float NATIVE_SMALL_COVER_CORNER_DP=12f;
 static final CoverHandoff FALLBACK=new CoverHandoff(DiscTransition.COVER_START_SCALE);
 record Translation(float x,float y) {}
 record ReturnAdjustment(float x,float y,float sideDelta) {
  static final ReturnAdjustment ZERO=new ReturnAdjustment(0,0,0);
  boolean closeTo(ReturnAdjustment other){
   return Math.abs(x-other.x)<=.75f&&Math.abs(y-other.y)<=.75f
       &&Math.abs(sideDelta-other.sideDelta)<=.75f;
  }
 }

 /** Follow a settled native cover's position and small changes in its long side. */
 static final class ReturnPosition {
  private static final long ADJUST_NS=80_000_000L;
  private Rect previousNative;
  private ReturnAdjustment measured,from=ReturnAdjustment.ZERO,target=ReturnAdjustment.ZERO;
  private long started;

  ReturnAdjustment observe(Rect origin,Rect nativeBounds,long now){
   ReturnAdjustment current=adjustment(now);
   measured=origin!=null&&within(previousNative,nativeBounds,.75f)?new ReturnAdjustment(
       centerX(nativeBounds)-centerX(origin),centerY(nativeBounds)-centerY(origin),
       longestSide(nativeBounds)-longestSide(origin)):null;
   previousNative=nativeBounds;
   if(measured!=null&&!target.closeTo(measured)){
    from=current;target=measured;started=now;
   }
   return current;
  }

  boolean stable(){return measured!=null;}
  boolean aligned(ReturnAdjustment published){return measured!=null&&published.closeTo(measured);}
  void reset(){
   previousNative=null;measured=null;from=target=ReturnAdjustment.ZERO;started=0;
  }

  private ReturnAdjustment adjustment(long now){
   if(from.equals(target)||now-started>=ADJUST_NS)return target;
   float t=Math.max(0,(float)((double)(now-started)/ADJUST_NS));
   float eased=t*t*(3-2*t);
   return new ReturnAdjustment(from.x()+(target.x()-from.x())*eased,
       from.y()+(target.y()-from.y())*eased,from.sideDelta()+(target.sideDelta()-from.sideDelta())*eased);
  }
 }

 static boolean visibleOrigin(Rect bounds,float viewportWidth,float viewportHeight){
  return bounds!=null&&bounds.getWidth()>0&&bounds.getHeight()>0
      &&bounds.getLeft()>=-1&&bounds.getTop()>=-1
      &&bounds.getRight()<=viewportWidth+1&&bounds.getBottom()<=viewportHeight+1;
 }

 // The captured cover may still include a small native scale effect or a
 // different artwork aspect. Reject other placements before ranking nodes.
 static boolean matchesReturnSlot(Rect origin,Rect bounds){
  float originalSide=longestSide(origin);
  float maxDrift=Math.max(8,Math.min(24,originalSide*.04f));
  float maxSizeDrift=Math.max(8,Math.min(16,originalSide*.025f));
  return Math.abs(longestSide(bounds)-originalSide)<=maxSizeDrift
      &&Math.abs(centerX(origin)-centerX(bounds))<=maxDrift
      &&Math.abs(centerY(origin)-centerY(bounds))<=maxDrift;
 }

 private static boolean within(Rect original,Rect current,float tolerance){
  return original!=null&&current!=null&&Math.abs(original.getLeft()-current.getLeft())<=tolerance
      &&Math.abs(original.getTop()-current.getTop())<=tolerance
      &&Math.abs(original.getRight()-current.getRight())<=tolerance
      &&Math.abs(original.getBottom()-current.getBottom())<=tolerance;
 }

 private static float centerX(Rect bounds){return (bounds.getLeft()+bounds.getRight())*.5f;}
 private static float centerY(Rect bounds){return (bounds.getTop()+bounds.getBottom())*.5f;}

 private static float longestSide(Rect bounds){return Math.max(bounds.getWidth(),bounds.getHeight());}

 static CoverHandoff fromSlot(Rect bounds,float side,float sideDelta){
  return new CoverHandoff((longestSide(bounds)+sideDelta)/side);
 }

 static Translation translationFrom(Rect bounds,SelfDrawLayout plan){
  float x=centerX(bounds)-Math.round(plan.left())-plan.width()*.5f;
  float y=centerY(bounds)-Math.round(plan.top())-plan.side()*.5f;
  return new Translation(x,y);
 }

 static CoverHandoff from(Rect nativeBounds,float side,float aspect){
  if(nativeBounds==null)return FALLBACK;
  float scale=Math.min(nativeBounds.getWidth()/RootRenderer.fittedWidth(side,aspect),
      nativeBounds.getHeight()/RootRenderer.fittedHeight(side,aspect));
  if(!Float.isFinite(scale)||scale<=0)return FALLBACK;
  return new CoverHandoff(scale);
 }

 static float remaining(float animatedScale){
  return Math.max(0f,Math.min(1f,(1-animatedScale)/(1-DiscTransition.COVER_START_SCALE)));
 }

 // The Canvas transform scales the clip with the cover. Convert the target
 // on-screen radius back to local pixels so the handoff matches at any scale.
 static float coverCornerRadius(float scale,float density){
  return NATIVE_SMALL_COVER_CORNER_DP*density/scale;
 }

 float animatedScale(float remaining){return 1+(scale-1)*remaining;}
}
