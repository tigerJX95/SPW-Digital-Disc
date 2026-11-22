package io.github.tigerjx95.spwcdprobe;

/** The same three window outlines drive the reel and the framing preview. */
final class ReelGeometry {
 static final float FACE_RADIUS=.985f;
 static final float SPINDLE_RADIUS=.057f;
 static final float HUB_RADIUS=.199f;

 interface WindowPath {
  void moveTo(float x,float y);
  void lineTo(float x,float y);
  void corner(float x,float y,float nextX,float nextY);
  void arc(float radius,float start,float sweep);
  void close();
 }
 private ReelGeometry(){}

 static void windows(float radius,WindowPath path){
  float outer=radius*.866f,inner=radius*.435f,bevel=radius*.018f;
  for(int middle=90;middle<360;middle+=120){
   float start=middle-13,end=middle+13;
   path.moveTo(x(outer,start+2),y(outer,start+2));
   path.arc(outer,start+2,22);
   corner(path,outer,end,outer-bevel,end);
   path.lineTo(x(inner+bevel,end),y(inner+bevel,end));
   corner(path,inner,end,inner,end-3);
   path.arc(inner,end-3,-20);
   corner(path,inner,start,inner+bevel,start);
   path.lineTo(x(outer-bevel,start),y(outer-bevel,start));
   corner(path,outer,start,outer,start+2);
   path.close();
  }
 }
 private static void corner(WindowPath path,float radius,float degrees,float nextRadius,float nextDegrees){
  path.corner(x(radius,degrees),y(radius,degrees),x(nextRadius,nextDegrees),y(nextRadius,nextDegrees));
 }
 private static float x(float radius,float degrees){return radius*(float)Math.cos(Math.toRadians(degrees));}
 private static float y(float radius,float degrees){return radius*(float)Math.sin(Math.toRadians(degrees));}
}
