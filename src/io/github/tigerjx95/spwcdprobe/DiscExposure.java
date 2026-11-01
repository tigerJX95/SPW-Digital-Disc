package io.github.tigerjx95.spwcdprobe;
/** Final disc travel, independent of the existing normalized animation progress. */
final class DiscExposure {
 static final int DEFAULT=50;
 static final String KEY="disc_exposure";
 static int percent(Object value){
  double number;
  try{number=value instanceof Number n?n.doubleValue():value instanceof String s?Double.parseDouble(s.trim()):DEFAULT;}
  catch(NumberFormatException ignored){return DEFAULT;}
  if(!Double.isFinite(number))return DEFAULT;
  return (int)Math.round(Math.max(0,Math.min(100,number)));
 }
 static float fraction(int percent){return Math.max(0,Math.min(100,percent))/100f;}
 static float widthFactor(int percent){return 1f+fraction(percent);}
 static float center(float side,float progress,int percent){return side*(.5f+fraction(percent)*Math.max(0,Math.min(1,progress)));}
}
