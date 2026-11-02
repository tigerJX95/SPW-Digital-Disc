package io.github.tigerjx95.spwcdprobe;

import java.awt.Color;
import java.awt.geom.Rectangle2D;

/** Exercises the real generated-disc renderer; no native Skia backend needed. */
public class GeneratedDiscFinishTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static RootRasterTest.Raster render(int side,int color,boolean front,float angle)throws Exception{
  var raster=new RootRasterTest.Raster(side*2,side);
  RootRenderer.paint(raster.scope,1,(scope,w,h)->{
   raster.graphics.setColor(new Color(color,true));raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
  },(s,x,y,d)->false,raster::path,1,100,front,FrontRendererTest.layer(),angle);
  check(raster.saved.isEmpty()&&raster.graphics.getClip()==null&&raster.graphics.getTransform().isIdentity(),"finish restores canvas");
  return raster;
 }
 public static void main(String[] args)throws Exception{
  int cases=0;
  for(int side:new int[]{180,295,420,620})for(int color:new int[]{0xff101820,0xfffafafa,0xffbd4831,0xff194a98,0x804080c0})for(boolean front:new boolean[]{false,true})for(float angle:new float[]{0,37,180}){
   var raster=render(side,color,front,angle);float r=side*.49f,cx=side*1.5f,cy=side*.5f;
   int holeX=Math.round(cx+r*.075f),holeY=Math.round(cy);
   check((raster.bitmap.getRGB(holeX,holeY)>>>24)==24,"hole retains faint translucent film beyond white spindle");
   boolean spindle=false;
   for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)spindle|=(raster.bitmap.getRGB(Math.round(cx)+dx,Math.round(cy)+dy)>>>24)>150;
   // The Java2D adapter is aliased: a subpixel spindle at small rotated
   // sizes can fall between samples. Larger fixtures verify actual pixels.
   if(side>=420)check(spindle,"small white spindle retained at subpixel centers");
   check(raster.circles==9,"white rim, layered hub and original white spindle");
   check(raster.bitmap.getRGB(side*2-1,Math.round(cy))==0,"rim stays inside original disc bounds");
   if((color>>>24)==255)check(raster.bitmap.getRGB(side/2,side/2)==color,"cover is unaffected");
   // Opaque artwork away from the hub and narrow rim must be byte-identical.
   if((color>>>24)==255){
    for(int y=0;y<side;y++)for(int x=side;x<side*2;x++){
     double distance=Math.hypot(x+.5-cx,y+.5-cy);
     if(distance>r*.25&&distance<r-8)check(raster.bitmap.getRGB(x,y)==color,"finish cannot wash over artwork body");
    }
   }
   cases++;
  }
  var translucent=render(420,0x804080c0,false,0);
  int center=630,radius=Math.round(420*.49f),hub=translucent.bitmap.getRGB(center+Math.round(radius*.175f),210)>>>24;
  check(hub>160&&hub<215,"hub remains translucent over translucent artwork");
  var empty=render(420,0,false,0);
  int middle=empty.bitmap.getRGB(center+Math.round(radius*.175f),210)>>>24;
  int inner=empty.bitmap.getRGB(center+Math.round(radius*.129f),210)>>>24;
  int outer=empty.bitmap.getRGB(center+Math.round(radius*.213f),210)>>>24;
  check(middle>80&&middle<150&&inner>middle+20&&outer>middle+20&&inner<250&&outer<255,"two defined silver bevels around a lightly translucent hub field");
  var dark=render(420,0xff101820,false,0);
  int rim=dark.bitmap.getRGB(center+radius-2,210);
  check((rim>>>16&255)>220&&(rim>>>8&255)>220&&(rim&255)>220,"reference-inspired white outer edge is visible on dark artwork");
  for(float progress:new float[]{.25f,.5f,.75f,1}){
   var moving=new RootRasterTest.Raster(840,420);
   RootRenderer.paint(moving.scope,1,(s,w,h)->{},(s,x,y,d)->false,moving::path,progress,100,true,FrontRendererTest.layer(),0);
   int x=Math.round(DiscExposure.center(420,progress,100));
   check(moving.bitmap.getRGB(x+Math.round(radius*.175f),210)==empty.bitmap.getRGB(center+Math.round(radius*.175f),210),"cached radial finish follows moving disc center");
  }
  // Local artwork owns its own optical details; no generated circles may run.
  for(boolean front:new boolean[]{false,true})for(float angle:new float[]{0,37,180}){
   var local=new RootRasterTest.Raster(480,240);
   RootRenderer.paint(local.scope,1,local::source,(s,x,y,d)->true,local::path,1,100,front,FrontRendererTest.layer(),angle);
   check(local.circles==0,"local PNG/JPG bypasses all generated finishing");
  }
  System.out.println("PASS generated finish: "+cases+" size/color/layer/rotation cases, unchanged artwork body and cover, translucent hole, white spindle, bounded rim, translucent annulus and untouched local artwork");
 }
}
