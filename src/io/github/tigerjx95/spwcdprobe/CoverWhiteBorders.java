package io.github.tigerjx95.spwcdprobe;

/** Detect opaque near-white cover margins. */
final class CoverWhiteBorders {
 record Bounds(int x,int y,int width,int height){
  boolean trimmedFrom(int originalWidth,int originalHeight){return width<originalWidth||height<originalHeight;}
  float displayAspect(){
   // Fill the square only when doing so changes proportions by at most 10%.
   return Math.max(width,height)<=Math.min(width,height)*1.10f?1f:(float)width/height;
  }
 }
 enum Mode { OFF, PAIRED, RIGHT }
 interface Pixels {int color(int x,int y);}
 private CoverWhiteBorders(){}
 static Bounds find(int width,int height,Pixels pixels){
  return find(width,height,pixels,Mode.PAIRED);
 }
 static Bounds find(int width,int height,Pixels pixels,Mode mode){
  if(mode==Mode.RIGHT){
   int minTrim=Math.max(2,width/64);
   int maxTrim=width*2/5;
   int left=edge(width,height,pixels,false,false,true);
   if(left<minTrim||left>maxTrim)return new Bounds(0,0,width,height);
   int right=edge(width,height,pixels,false,true,true);
   if(right>=minTrim&&right<=maxTrim){
    right=finishRightEdge(width,height,pixels,right,maxTrim);
    return new Bounds(0,0,width-right,height);
   }
  }
  if(mode!=Mode.PAIRED)return new Bounds(0,0,width,height);
  int left=edge(width,height,pixels,false,false),right=edge(width,height,pixels,false,true);
  if(pair(left,right,width))return new Bounds(left,0,width-left-right,height);
  int top=edge(height,width,pixels,true,false,true),bottom=edge(height,width,pixels,true,true,true);
  // A title or other detail can interrupt white rows before the artwork starts.
  if(top!=edge(height,width,pixels,true,false)||bottom!=edge(height,width,pixels,true,true))
   return new Bounds(0,0,width,height);
  if(pair(top,bottom,height))return new Bounds(0,top,width,height-top-bottom);
  return new Bounds(0,0,width,height);
 }
 private static int finishRightEdge(int width,int height,Pixels pixels,int trim,int maxTrim){
  // A confirmed blank margin can end in a thin, nearly white transition.
  int limit=Math.min(maxTrim,trim+Math.max(2,width/128));
  for(;trim<limit;trim++){
   int x=width-1-trim;
   int nonWhite=0;
   for(int y=0;y<height;y++){
    if(!isNearWhite(pixels.color(x,y),228)&&++nonWhite>height/50)return trim;
   }
  }
  return trim;
 }
 private static boolean isNearWhite(int color,int minimum){
  return (color>>>24)>=250&&(color>>>16&255)>=minimum&&(color>>>8&255)>=minimum&&(color&255)>=minimum;
 }
 private static boolean pair(int first,int second,int size){
  return Math.min(first,second)>=Math.max(2,size/64)
   &&Math.max(first,second)<=size*2/5
   &&Math.abs(first-second)<=Math.max(2,Math.max(first,second)/4);
 }
 private static int edge(int depth,int span,Pixels pixels,boolean horizontal,boolean reverse){return edge(depth,span,pixels,horizontal,reverse,false);}
 private static int edge(int depth,int span,Pixels pixels,boolean horizontal,boolean reverse,boolean strict){
  int limit=depth*2/5+1;
  int samples=strict?span:Math.min(33,span);
  for(int offset=0;offset<limit&&offset<depth;offset++){
   int coordinate=reverse?depth-1-offset:offset;
   int white=0;
   for(int sample=0;sample<samples;sample++){
    int across=samples==1?0:sample*(span-1)/(samples-1);
    int color=horizontal?pixels.color(across,coordinate):pixels.color(coordinate,across);
    if(isNearWhite(color,238))white++;
    else if(strict)return offset;
   }
   if(white*100<samples*90)return offset;
  }
  return limit;
 }
}
