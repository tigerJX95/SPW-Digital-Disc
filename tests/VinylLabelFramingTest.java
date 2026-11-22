package io.github.tigerjx95.spwcdprobe;

public class VinylLabelFramingTest {
 public static void main(String[] args){
  var margins=new VinylLabelFraming.Crop(87,97,92,80);
  var frame=new VinylLabelFraming(margins,false,125,.45f,-.3f);
  check(VinylLabelFraming.decode(frame.encode()).equals(frame),"independent colored margins, pan and zoom round-trip");
  check(margins.pixels(1000,1000).equals(new CoverWhiteBorders.Bounds(87,97,821,823)),"asymmetric margins retain the inset image");
  var narrow=new VinylLabelFraming.Crop(242,0,242,0);
  var fit=new VinylLabelFraming(narrow,true,100,0,0);
  var window=fit.place(.516f,200);
  check(Math.abs(Math.hypot(window.width(),window.height())-200)<.01,"a fitted portrait stays within the circle");
  check(window.width()<window.height()&&window.x()==-window.width()*.5f,"portrait proportions and center are preserved");
  var fill=new VinylLabelFraming(narrow,false,150,1,-1).place(.516f,200);
  check(fill.x()<=-99.99f&&fill.y()<=-99.99f&&fill.x()+fill.width()>=99.99f&&fill.y()+fill.height()>=99.99f,
      "pan stops before any fill-mode gaps appear");
  check(VinylLabelFraming.decode("950,0,50,0,true,100,0,0").equals(VinylLabelFraming.DEFAULT),"an invalid crop falls back safely");
  var tiny=new VinylLabelFraming.Crop(900,900,0,0).pixels(1,1);
  check(tiny.equals(new CoverWhiteBorders.Bounds(0,0,1,1)),"tiny covers still have a valid source rectangle");
  System.out.println("PASS colored label crop, portrait fit, bounded pan and serialized framing");
 }

 private static void check(boolean condition,String reason){
  if(!condition)throw new AssertionError(reason);
 }
}
