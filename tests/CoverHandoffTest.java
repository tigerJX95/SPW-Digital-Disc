package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.geometry.Rect;

final class CoverHandoffTest {
 public static void main(String[] args){
  var nativeBounds=new Rect(35,111,498,574);
  float side=678;
  var handoff=CoverHandoff.from(nativeBounds,side,1);
  var centered=CoverHandoff.from(new Rect(107,107,570,570),side,1);
  if(!handoff.equals(centered))throw new AssertionError("outgoing lyrics position must not pull the cover off center");
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

  var portrait=CoverHandoff.from(new Rect(100,100,300,500),side,.5f);
  near(portrait.scale()*side,400,"fitted portrait size");
  if(CoverHandoff.from(new Rect(1,1,1,1),side,1)!=CoverHandoff.FALLBACK)
   throw new AssertionError("unmeasured native geometry should retain the existing fallback");
  System.out.println("PASS: centered size handoff and page-entry redraw scope, independent of the track-entry route");
 }

 private static void near(float actual,float expected,String label){
  if(Math.abs(actual-expected)>.001f)throw new AssertionError(label+": "+actual+" != "+expected);
 }
}
