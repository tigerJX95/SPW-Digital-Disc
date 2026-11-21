package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.geometry.Rect;

final class CoverHandoffTest {
 public static void main(String[] args){
  var nativeBounds=new Rect(35,111,498,574);
  float side=678;
  var handoff=CoverHandoff.from(nativeBounds,side,1);
  if(!CoverHandoff.visibleOrigin(nativeBounds,1080,720)
      ||CoverHandoff.visibleOrigin(new Rect(-190,100,200,490),1080,720)
      ||CoverHandoff.visibleOrigin(new Rect(100,100,100,100),1080,720))
   throw new AssertionError("only measurable covers inside the viewport can anchor the transition");
  for(int amount:new int[]{0,25,50,100}){
   var plan=SelfDrawLayout.fit(1080,720,1,true,amount);
   var origin=CoverHandoff.translationFrom(nativeBounds,plan);
   float x=Math.round(plan.left())+plan.width()*.5f;
   float y=Math.round(plan.top())+plan.side()*.5f;
   near(x+origin.x(),266.5f,"first frame starts at the original horizontal center");
   near(y+origin.y(),342.5f,"first frame starts at the original vertical center");
   float pivot=plan.side()*(.5f+DiscExposure.fraction(amount)*.5f);
   near(Math.round(plan.left())+pivot,x,"slide ends at the cover's centered rendering pivot");
   near(x+origin.x()*.5f,(x+266.5f)*.5f,"halfway horizontal slide");
   near(y+origin.y()*.5f,(y+342.5f)*.5f,"halfway vertical slide");
  }
  var motion=new DiscTransition();
  motion.enter();
  float beginning=CoverHandoff.remaining(motion.pose().scale());
  near(handoff.animatedScale(beginning)*side,nativeBounds.getWidth(),"starting size");
  if(motion.pageEntryAnimating())throw new AssertionError("waiting must not request animation draws");

  long now=1_000_000_000L;
  motion.ready(now);
  if(!motion.pageEntryAnimating())throw new AssertionError("cover enlargement needs redraws");
  motion.tick(now+120_000_000L);
  float midway=CoverHandoff.remaining(motion.pose().scale());
  near(handoff.animatedScale(midway)*side,(side+463)*.5f,"halfway size");
  near(midway,.5f,"position uses the same timing as size");
  near(motion.pose().exposed(),0,"disc hidden during enlargement");
  motion.tick(now+240_000_000L);
  near(CoverHandoff.remaining(motion.pose().scale()),0,"no native geometry after enlargement");
  near(motion.pose().exposed(),0,"full size before disc travel");
  if(!motion.pageEntryAnimating())throw new AssertionError("page-entry disc travel needs redraws");
  motion.tick(now+940_000_000L);
  if(motion.pageEntryAnimating())throw new AssertionError("settled page must stop requesting entry draws");
  motion.trackChanged();
  near(CoverHandoff.remaining(motion.pose().scale()),0,"track entry retains the full cover");
  motion.trackReady(now+1_000_000_000L);
  if(motion.pageEntryAnimating())throw new AssertionError("track entry retains its normal redraw route");

  motion.enter();
  motion.ready(now);
  motion.tick(now+120_000_000L);
  motion.close(now+120_000_000L,()->{});
  near(CoverHandoff.remaining(motion.pose().scale()),.5f,"interrupted return starts at the current position");
  motion.tick(now+300_000_000L);
  near(CoverHandoff.remaining(motion.pose().scale()),1,"interrupted return reaches the native origin");

  var portrait=CoverHandoff.from(new Rect(100,100,300,500),side,.5f);
  near(portrait.scale()*side,400,"fitted portrait size");
  if(CoverHandoff.from(new Rect(1,1,1,1),side,1)!=CoverHandoff.FALLBACK)
   throw new AssertionError("unmeasured native geometry should retain the existing fallback");
  System.out.println("PASS: native-position and size handoff, uninterrupted eject timing, early return and independent track entry");
 }

 private static void near(float actual,float expected,String label){
  if(Math.abs(actual-expected)>.001f)throw new AssertionError(label+": "+actual+" != "+expected);
 }
}
