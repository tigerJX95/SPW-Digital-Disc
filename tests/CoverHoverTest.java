package io.github.tigerjx95.spwcdprobe;

public final class CoverHoverTest {
 private static final long FRAME=16_000_000L;
 public static void main(String[] args){
  var hover=new CoverHover();
  hover.bounds(22,40,248,310);
  hover.move(146,195);
  check(hover.sample(FRAME)==CoverHover.Tilt.FLAT,"the center stays face-on");
  hover.move(252,60);
  var entering=hover.sample(FRAME*2);
  check(entering.x()>0&&entering.x()<.85f&&entering.y()<0,"pointer motion eases toward the hovered corner");
  for(int i=3;i<=44;i++)hover.sample(i*FRAME);
  var held=hover.sample(FRAME*45);
  check(held.x()>.8f&&held.y()<-.8f&&hover.sample(FRAME*46)==held,"holding the pointer settles without continuous redraws");
  for(float x:new float[]{-1,1})for(float y:new float[]{-1,1}){
   var matrix=new float[16];
   new CoverHover.Tilt(x,y).writeMatrix(matrix,248,310,1f);
   float hovered=1+x*124*matrix[3]+y*155*matrix[7];
   float opposite=1-x*124*matrix[3]-y*155*matrix[7];
   check(matrix[0]/hovered<1&&hovered>1&&hovered<1.09f&&opposite<1,
       "perspective foreshortens the hovered corner in each quadrant");
  }
  hover.move(300,170);
  check(hover.sample(FRAME*47)==held,"exit starts from the displayed pose");
  var leaving=hover.sample(FRAME*57);
  check(leaving.x()>0&&leaving.x()<held.x(),"leaving eases towards rest");
  check(hover.sample(FRAME*87)==CoverHover.Tilt.FLAT,"leaving restores the precise rest pose");
  hover.move(250,44);hover.sample(FRAME*88);hover.reset();
  hover.bounds(0,0,250,250);
  check(hover.sample(FRAME*89)==CoverHover.Tilt.FLAT,"a new presentation cannot reuse the old pointer");
  checkReturn(hover,held);
  checkPointerCircle(8_333_333L);
  checkPointerCircle(16_666_667L);

  checkRecordedPose();
  checkBottomSweep(680,680);
  checkBottomSweep(350,680);
  checkBottomSweep(680,350);
  System.out.println("PASS sleeve hover: four corners retreat, held pose settles, pointer exit eases and retraction returns smoothly");
 }
 private static void checkPointerCircle(long frameInterval){
  var hover=new CoverHover();
  hover.bounds(0,0,400,400);
  long start=1_000_000_000L;
  hover.move(200,200);hover.sample(start);
  for(long elapsed=frameInterval;elapsed<=1_500_000_000L;elapsed+=frameInterval){
   double angle=elapsed*2*Math.PI/500_000_000d;
   float x=.72f*(float)Math.cos(angle),y=.72f*(float)Math.sin(angle);
   hover.move(200*(1+x),200*(1+y));
   var tilt=hover.sample(start+elapsed);
   if(elapsed>=600_000_000L){
    check(Math.abs(x-tilt.x())<.00001&&Math.abs(y-tilt.y())<.00001,"settled hover uses the latest position");
   }
  }
 }
 // Primary page near 3.17 s: moving left across the bottom, the upper
 // edge slopes up to the right and the lower edge slopes down to the right.
 private static void checkRecordedPose(){
  for(float side:new float[]{464,680}){
   float[] m=new float[16];
   new CoverHover.Tilt(-.66f,.83f).writeMatrix(m,side,side,464f/side);
   float h=side*.5f;
   float topWidth=projectX(m,h,-h)-projectX(m,-h,-h);
   float bottomWidth=projectX(m,h,h)-projectX(m,-h,h);
   check(Math.abs(topWidth-bottomWidth-32)<2,"recorded top and bottom width difference in pixels");
   check(Math.abs(projectY(m,h,-h)-projectY(m,-h,-h)+10)<2,"recorded top edge tilt in pixels");
   check(Math.abs(projectY(m,h,h)-projectY(m,-h,h)-14)<2,"recorded bottom edge tilt in pixels");
  }
 }
 private static void checkBottomSweep(float width,float height){
  float[] m=new float[16];
  float w=width*.5f,h=height*.5f;
  float nativeScale=464f/680f;
  for(float y:new float[]{.8f,.9f,1f})for(int i=-10;i<=10;i++){
   float x=i/10f;
   new CoverHover.Tilt(x,y).writeMatrix(m,width,height,nativeScale);
   float top=projectX(m,w,-h)-projectX(m,-w,-h);
   float bottom=projectX(m,w,h)-projectX(m,-w,h);
   // At 85% down the native half-height, the recorded 464px reference
   // has about 32px width difference. Limit it to the same physical motion.
   check(top>bottom&&top-bottom<width*nativeScale*.085f,"bottom narrows within the native displacement budget");
   float upperSlope=projectY(m,w,-h)-projectY(m,-w,-h);
   float lowerSlope=projectY(m,w,h)-projectY(m,-w,h);
   if(x!=0){
    check(upperSlope*x>0&&lowerSlope*x<0,"both edges turn through a horizontal reversal");
    check(Math.abs(lowerSlope)>Math.abs(upperSlope)*.5f,"bottom edge participates in tilt");
   }
   check(projectX(m,0,0)==0&&projectY(m,0,0)==0,"image rotates around its center");
  }
  CoverHover.Tilt.FLAT.writeMatrix(m,width,height,nativeScale);
  check(projectX(m,-w,h)==-w&&projectY(m,-w,h)==h,"flat pose restores the reused matrix");
 }
 private static float projectX(float[] m,float x,float y){
  return m[0]*x/(m[3]*x+m[7]*y+1);
 }
 private static float projectY(float[] m,float x,float y){
  return (m[1]*x+m[5]*y)/(m[3]*x+m[7]*y+1);
 }
 private static void checkReturn(CoverHover hover,CoverHover.Tilt displayed){
  long start=2_000_000_000L;
  hover.beginReturn(displayed,start);
  check(hover.sampleReturn(start)==displayed,"first retraction frame starts from the displayed tilt");
  float previous=displayed.x();
  for(long dt=1_000_000L;dt<CoverHover.RETURN_NS;dt+=1_000_000L){
   var returning=hover.sampleReturn(start+dt);
   check(returning.x()>0&&returning.x()<previous&&returning.y()<0,"return stays on the same axis without overshooting");
   previous=returning.x();
  }
  check(hover.sampleReturn(start+CoverHover.RETURN_NS)==CoverHover.Tilt.FLAT,"return is flat at 120 ms");
 }
 private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
}
