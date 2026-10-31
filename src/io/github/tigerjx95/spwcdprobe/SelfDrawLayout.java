package io.github.tigerjx95.spwcdprobe;
/** Actual independently measured Canvas bounds. The native cover size is not an input. */
record SelfDrawLayout(float left,float top,float side,float width,float height,boolean secondary) {
 static SelfDrawLayout fit(float viewportWidth,float viewportHeight,float density,boolean secondary){
  float padding=24*density,bottom=100*density,topPadding=32*density;
  float paneWidth=secondary?viewportWidth:viewportWidth*.5f;
  float usableHeight=Math.max(0,viewportHeight-bottom-topPadding);
  float wanted=viewportHeight*(secondary?.66f:.45f);
  float side=Math.max(0,Math.min(wanted,Math.min(usableHeight,(paneWidth-2*padding)/1.5f)));
  float width=side*1.5f;
  float centerX=secondary?viewportWidth*.5f:viewportWidth*.25f;
  float top=topPadding+(usableHeight-side)*.5f;
  float gap=4*density;
  float reflection=secondary?Math.max(0,Math.min(64*density,Math.min(side*.12f,viewportHeight-bottom-top-side-gap))):0;
  return new SelfDrawLayout(centerX-width*.5f,top,side,width,side+(reflection>0?gap+reflection:0),secondary);
 }
 boolean usable(float density){return side>=120*density;}
}
