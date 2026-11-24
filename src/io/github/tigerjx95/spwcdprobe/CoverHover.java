package io.github.tigerjx95.spwcdprobe;

/** Pointer tracking and easing for the open sleeve, in Canvas coordinates. */
final class CoverHover {
 static final long RETURN_NS=120_000_000L;
 private static final long HOVER_FADE_NS=600_000_000L;

 record Tilt(float x,float y){
  static final Tilt FLAT=new Tilt(0,0);
  void writeMatrix(float[] matrix,float width,float height,float nativeScale){
   // The primary cover uses centered X/Y rotation, up to six degrees on
   // each axis. Limit displacement on the enlarged sleeve to its native size.
   double radians=Math.toRadians(6)*Math.min(1f,nativeScale);
   double pitch=-y*radians,yaw=x*radians;
   float sx=(float)Math.sin(pitch),cx=(float)Math.cos(pitch);
   float sy=(float)Math.sin(yaw),cy=(float)Math.cos(yaw);
   // The primary-page recording at full hover gives a depth gradient
   // of about .08 per cover width at six degrees (0.78 * sin(6 deg)).
   float inverseDistance=.78f/Math.max(width,height);
   matrix[0]=cy;
   matrix[1]=sx*sy;
   matrix[5]=cx;
   matrix[3]=cx*sy*inverseDistance;
   matrix[7]=-sx*inverseDistance;
   matrix[10]=matrix[15]=1f;
  }
 }
 private float left,top,width,height;
 private float pointerX=Float.NaN,pointerY=Float.NaN;
 private boolean pointerOverCover;
 private float targetX,targetY;
 private boolean fadingIn;
 private float gain,fadeFrom;
 private long fadeStarted;
 private Tilt tilt=Tilt.FLAT;
 private Tilt returnFrom=Tilt.FLAT;
 private long returnStarted;

 void bounds(float left,float top,float width,float height){
  if(this.left==left&&this.top==top&&this.width==width&&this.height==height)return;
  this.left=left;this.top=top;this.width=width;this.height=height;
  updateTarget();
 }
 void move(float x,float y){
  pointerX=x;pointerY=y;
  updateTarget();
 }
 private void updateTarget(){
  pointerOverCover=width>0&&height>0&&pointerX>=left&&pointerX<=left+width&&pointerY>=top&&pointerY<=top+height;
  if(pointerOverCover){
   targetX=2f*(pointerX-left)/width-1f;
   targetY=2f*(pointerY-top)/height-1f;
  }
 }
 void leave(){move(Float.NaN,Float.NaN);}
 boolean moving(){
  return pointerOverCover!=fadingIn || ((targetX!=0||targetY!=0)&&gain!=(fadingIn?1f:0f))
      || tilt.x()!=targetX*gain || tilt.y()!=targetY*gain;
 }
 void reset(){
  leave();fadingIn=false;gain=fadeFrom=targetX=targetY=0;
  tilt=returnFrom=Tilt.FLAT;width=height=0;
 }

 void beginReturn(Tilt displayed,long now){
  leave();
  returnFrom=displayed;
  returnStarted=now;
 }

 Tilt sampleReturn(long now){
  if(returnFrom==Tilt.FLAT||now<=returnStarted)return returnFrom;
  long elapsed=now-returnStarted;
  if(elapsed>=RETURN_NS){
   returnFrom=Tilt.FLAT;
   return returnFrom;
  }
  float t=(float)elapsed/RETURN_NS;
  // Smoothstep leaves and reaches rest with zero angular velocity.
  float remaining=1f-t*t*(3f-2f*t);
  return new Tilt(returnFrom.x()*remaining,returnFrom.y()*remaining);
 }

 Tilt sample(long now){
  gain=gainAt(now);
  if(gain==(fadingIn?1f:0f))fadeFrom=gain;
  if(pointerOverCover!=fadingIn){
   fadingIn=pointerOverCover;
   fadeFrom=gain;
   fadeStarted=now;
  }
  float x=targetX*gain,y=targetY*gain;
  if(x==tilt.x()&&y==tilt.y())return tilt;
  return tilt=x==0&&y==0?Tilt.FLAT:new Tilt(x,y);
 }

 private float gainAt(long now){
  float goal=fadingIn?1f:0f;
  if(fadeFrom==goal||now-fadeStarted>=HOVER_FADE_NS)return goal;
  if(now<=fadeStarted)return fadeFrom;
  float time=(float)(now-fadeStarted)/HOVER_FADE_NS;
  // Compose tween's default FastOutSlowIn easing, cubic (0.4, 0, 0.2, 1).
  float lo=0,hi=1;
  for(int i=0;i<14;i++){
   float t=(lo+hi)*.5f,u=1f-t;
   if(t*(1.2f*u*u+.6f*u*t+t*t)<time)lo=t;else hi=t;
  }
  float t=(lo+hi)*.5f;
  return fadeFrom+(goal-fadeFrom)*t*t*(3f-2f*t);
 }
}
