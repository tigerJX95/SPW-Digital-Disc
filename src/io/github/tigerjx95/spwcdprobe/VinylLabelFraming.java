package io.github.tigerjx95.spwcdprobe;

/** Source margins use thousandths of the full cover. Pan uses the excess image size. */
record VinylLabelFraming(Crop crop, boolean fit, int zoom, float panX, float panY) {
 static final int MIN_ZOOM=100, MAX_ZOOM=300;
 static final VinylLabelFraming DEFAULT=new VinylLabelFraming(Crop.FULL,false,MIN_ZOOM,0,0);

 record Crop(int left,int top,int right,int bottom) {
  static final int UNIT=1_000, MAX_EDGE=900;
  static final Crop FULL=new Crop(0,0,0,0);

  Crop {
   if(Math.min(Math.min(left,right),Math.min(top,bottom))<0
       ||left>MAX_EDGE||right>MAX_EDGE||top>MAX_EDGE||bottom>MAX_EDGE
       ||left+right>MAX_EDGE||top+bottom>MAX_EDGE)
    throw new IllegalArgumentException("Invalid label crop margins");
  }

  CoverWhiteBorders.Bounds pixels(int width,int height){
   int x=Math.min(width-1,Math.round(left*width/(float)UNIT));
   int y=Math.min(height-1,Math.round(top*height/(float)UNIT));
   int w=width-x-Math.round(right*width/(float)UNIT);
   int h=height-y-Math.round(bottom*height/(float)UNIT);
   return new CoverWhiteBorders.Bounds(x,y,Math.max(1,w),Math.max(1,h));
  }

  static Crop fromPixels(CoverWhiteBorders.Bounds bounds,int width,int height){
   return new Crop(Math.round(bounds.x()*(float)UNIT/width),Math.round(bounds.y()*(float)UNIT/height),
       Math.round((width-bounds.x()-bounds.width())*(float)UNIT/width),
       Math.round((height-bounds.y()-bounds.height())*(float)UNIT/height));
  }
 }

 record Placement(float x,float y,float width,float height) {}

 VinylLabelFraming {
  java.util.Objects.requireNonNull(crop);
  zoom=Math.clamp(zoom,MIN_ZOOM,MAX_ZOOM);
  panX=boundedPan(panX);
  panY=boundedPan(panY);
 }

 private static float boundedPan(float value){
  return Float.isFinite(value)&&value!=0?Math.clamp(value,-1f,1f):0;
 }

 Placement place(float aspect,float diameter){
  float base=fit?(float)(diameter/Math.hypot(aspect,1)):diameter*Math.max(1,1/aspect)*zoom/MIN_ZOOM;
  float width=base*aspect,height=base;
  return new Placement(-width*.5f+panX*panRoom(width,diameter),-height*.5f+panY*panRoom(height,diameter),width,height);
 }

 static float panRoom(float imageSize,float diameter){return Math.max(0,(imageSize-diameter)*.5f);}

 String encode(){
  return crop.left()+","+crop.top()+","+crop.right()+","+crop.bottom()+","+fit+","+zoom+","+panX+","+panY;
 }

 static VinylLabelFraming decode(String value){
  String[] fields=value.split(",",-1);
  if(fields.length!=8||!fields[4].equals("true")&&!fields[4].equals("false"))return DEFAULT;
  try{
   var crop=new Crop(Integer.parseInt(fields[0]),Integer.parseInt(fields[1]),
       Integer.parseInt(fields[2]),Integer.parseInt(fields[3]));
   return new VinylLabelFraming(crop,Boolean.parseBoolean(fields[4]),Integer.parseInt(fields[5]),
       Float.parseFloat(fields[6]),Float.parseFloat(fields[7]));
  }catch(IllegalArgumentException invalid){return DEFAULT;}
 }
}
