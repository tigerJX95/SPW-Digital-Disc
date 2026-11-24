package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.drawscope.DrawScope;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
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
   check(raster.circles==9,"optical rim, layered hub and original white spindle");
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
  int rimLight=rim>>8&255;
  check(rimLight>220,"the original bright white rim is visible on dark artwork");
  check(dark.bitmap.getRGB(center+radius-5,210)==0xff101820,"narrow rim leaves artwork near the edge intact");
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
  checkPreparedArtwork();
  checkRotatingRim();
  checkCoverCdHub();
  System.out.println("PASS generated finish: "+cases+" size/color/layer/rotation cases, unchanged artwork body and cover, translucent hole, white spindle, bounded rim, translucent annulus and untouched local artwork");
 }
 // Exercise the shared compositor with the shipped texture on the Java2D canvas adapter.
 private static void checkCoverCdHub()throws Exception{
  var texture=javax.imageio.ImageIO.read(new java.io.File("plugin/cd-material/hub.png"));
  var bitmap=(androidx.compose.ui.graphics.ImageBitmap)java.lang.reflect.Proxy.newProxyInstance(
      GeneratedDiscFinishTest.class.getClassLoader(),new Class[]{androidx.compose.ui.graphics.ImageBitmap.class},
      (p,m,a)->switch(m.getName()){
       case "getWidth" -> texture.getWidth();
       case "getHeight" -> texture.getHeight();
       default -> throw new AssertionError(m);
      });
  int side=600,cx=900,cy=300;
  float radius=side*.49f;
  for(boolean cached:new boolean[]{false,true})for(float angle:new float[]{0,37,193}){
   var base=new RootRasterTest.Raster(side*2,side);
   var glazed=new RootRasterTest.Raster(side*2,side);
   for(var view:new RootRasterTest.Raster[]{base,glazed}){
    view.images.put(bitmap,texture);
    var disc=new RootRenderer.Disc(){
     public boolean draw(DrawScope scope,float x,float y,float d){return false;}
     public androidx.compose.ui.graphics.ImageBitmap coverCdHub(){return view==glazed?bitmap:null;}
     public boolean drawCoverCd(DrawScope scope,float x,float y,float d){
      if(cached){
       view.graphics.setColor(Color.DARK_GRAY);
       view.graphics.fill(new Rectangle2D.Float(x-d/2,y-d/2,d,d));
      }
      return cached;
     }
    };
    RootRenderer.paint(view.scope,1,(scope,w,h)->paintSwatches(view,w,h),disc,view::path,1,100,false,null,angle);
   }
   int ridge=0,field=0;
   for(int y=0;y<side;y++)for(int x=side;x<side*2;x++){
    int before=base.bitmap.getRGB(x,y),after=glazed.bitmap.getRGB(x,y);
    double dx=(x+.5-cx)/radius,dy=(y+.5-cy)/radius;
    double r=Math.hypot(dx,dy);
    if(before!=after){
     check(r>.123&&r<.207,"glaze must stay between the hole and the original silver rim");
     if(r>.128&&r<.142)ridge++;
     if(r>.15&&r<.19)field++;
    }
    if(r>.128&&r<.20){
     int tx=(int)((dx/.42+.5)*texture.getWidth()),ty=(int)((dy/.42+.5)*texture.getHeight());
     int texel=texture.getRGB(tx,ty);
     double alpha=(texel>>>24)/255.;
     double expected=(before&255)*(1-alpha)+(texel&255)*alpha;
     check(Math.abs((after&255)-expected)<4,"hub light must stay fixed through artwork rotation");
    }
   }
   check(ridge>200&&field>1000,"both the raised inner lip and curved reflection must be visible");
   base.graphics.dispose();glazed.graphics.dispose();
  }
  System.out.println("PASS curved CD hub and shallow relief: live and cached art, fixed lighting at three angles");
 }
 private static void checkPreparedArtwork()throws Exception{
  int side=400,radius=196,diameter=radius*2,cx=600,cy=200;
  for(float aspect:new float[]{.8f,1f,1.5f}){
   var live=new RootRasterTest.Raster(side*2,side);
   var prepared=new RootRasterTest.Raster(diameter,diameter);
   RootRenderer.paint(live.scope,aspect,(scope,w,h)->paintSwatches(live,w,h),(s,x,y,d)->false,
       live::path,1,100,false,null,0,DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE,false);
   RootRenderer.bakeCdArtwork(prepared.scope,(scope,w,h)->paintSwatches(prepared,w,h),aspect,diameter,prepared::path);
   var composed=renderPrepared(side,aspect,prepared,0,false);
   for(int y=0;y<side;y+=3)for(int x=side;x<side*2;x+=3){
    check(composed.bitmap.getRGB(x,y)==live.bitmap.getRGB(x,y),
        "prepared CD matches live crop, translucent center and finish: "+aspect+" at "+x+","+y);
   }
   check(prepared.bitmap.getRGB(0,0)==0xff315d90,"cached artwork spans the square behind the fixed rim");
   check((composed.bitmap.getRGB(side,0)>>>24)==0,"visible disc corners stay transparent");
   check((prepared.bitmap.getRGB(radius+15,radius)>>>24)==24,"prepared center keeps the transparent film");
  }
  var fit=new VinylLabelFraming(VinylLabelFraming.Crop.FULL,true,100,0,0);
  var live=new RootRasterTest.Raster(side*2,side);
  var prepared=new RootRasterTest.Raster(diameter,diameter);
  RootRenderer.paint(live.scope,1,framedSource(live,fit),(s,x,y,d)->false,
      live::path,1,100,false,null,0,DiscSettings.Style.CD,DiscSettings.VinylColor.BLUE,false);
  RootRenderer.bakeCdArtwork(prepared.scope,framedSource(prepared,fit),1,diameter,prepared::path);
  check(live.bitmap.getRGB(200,200)==Color.RED.getRGB(),"CD framing does not change the sleeve");
  check(live.bitmap.getRGB(cx+100,cy)==RootRenderer.CD_FACE_COLOR,"fit shows neutral CD face around the artwork");
  check(live.bitmap.getRGB(cx,cy-95)==Color.GREEN.getRGB(),"fit draws independent CD artwork");
  var composed=renderPrepared(side,1,prepared,0,false);
  for(int y=0;y<side;y+=3)for(int x=side;x<side*2;x+=3)
   check(composed.bitmap.getRGB(x,y)==live.bitmap.getRGB(x,y),"prepared CD matches manual framing");
  var reel=new RootRasterTest.Raster(side*2,side);
  RootRenderer.paint(reel.scope,1,framedSource(reel,fit),(s,x,y,d)->false,
      reel::path,1,100,false,null,0,DiscSettings.Style.REEL_COVER,DiscSettings.VinylColor.BLUE,false);
  check(reel.bitmap.getRGB(cx+116,cy)==RootRenderer.REEL_FIT_COLOR,"uncovered reel artwork has a neutral backing");
  check(reel.bitmap.getRGB(cx,cy-98)==Color.GREEN.getRGB(),"fit draws independent reel artwork");
  check(reel.bitmap.getRGB(cx,cy+110)!=Color.GREEN.getRGB(),"tape window remains visible over the disc artwork");
  check(reel.bitmap.getRGB(cx,cy)==0,"reel retains its center cutout");
  check(GeneratedCdRaster.Key.of(null,null,1f,400,null)==null,"missing cover retains the live fallback");
  System.out.println("PASS prepared CD reproduces live CD artwork, fixed rim, spindle and transparency");
 }

 private static RootRasterTest.Raster renderPrepared(int side,float aspect,RootRasterTest.Raster prepared,
     float angle,boolean custom)throws Exception{
  var raster=new RootRasterTest.Raster(side*2,side);
  int[] artworkDraws={0};
  var disc=new RootRenderer.Disc(){
   @Override public boolean draw(DrawScope scope,float x,float y,float d){return custom;}
   @Override public boolean drawCoverCd(DrawScope scope,float x,float y,float d){
    artworkDraws[0]++;
    check(raster.graphics.getClip()!=null,"prepared CD is drawn inside the fixed round mask");
    raster.graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    var imageTransform=AffineTransform.getTranslateInstance(x-d*.5f,y-d*.5f);
    imageTransform.scale(d/prepared.width,d/prepared.height);
    raster.graphics.drawImage(prepared.bitmap,imageTransform,null);
    return true;
   }
  };
  RootRenderer.paint(raster.scope,aspect,raster::source,disc,raster::path,1,100,false,null,angle);
  check(artworkDraws[0]==(custom?0:1),"custom discs bypass the cached artwork and cover rim");
  check(raster.circles==(custom?0:3),"when cached artwork is ready, only the three rim circles are redrawn");
  return raster;
 }

 private static void checkRotatingRim()throws Exception{
  int side=400,cx=600,cy=200,radius=196;
  var prepared=new RootRasterTest.Raster(radius*2,radius*2);
  RootRenderer.bakeCdArtwork(prepared.scope,(scope,w,h)->paintSwatches(prepared,w,h),1,radius*2,prepared::path);
  var upright=renderPrepared(side,1,prepared,0,false).bitmap;
  for(float angle:new float[]{7,19,37,61,86,123,207,317}){
   var rotated=renderPrepared(side,1,prepared,angle,false).bitmap;
   int changed=0,edgePixels=0;
   for(int y=0;y<side;y++)for(int x=side;x<side*2;x++){
    double distance=Math.hypot(x+.5-cx,y+.5-cy);
    if(distance>=radius-.45){
     int pixel=upright.getRGB(x,y);
     if((pixel>>>24)>0)edgePixels++;
     check(rotated.getRGB(x,y)==pixel,"rotation cannot move the white rim or leave pixels outside it");
    }
    if(distance<radius*.8&&rotated.getRGB(x,y)!=upright.getRGB(x,y))changed++;
   }
   check(edgePixels>50,"the comparison includes the visible rim pixels");
   check(changed>500,"artwork still rotates inside the stationary rim");
  }
  renderPrepared(side,1,prepared,37,true);
  System.out.println("PASS prepared CD rotates its artwork across eight angles with a stationary outer boundary");
 }

 private static RootRenderer.Source framedSource(RootRasterTest.Raster raster,VinylLabelFraming framing){
  return new RootRenderer.Source(){
   public void draw(androidx.compose.ui.graphics.drawscope.DrawScope scope,float w,float h){
    raster.graphics.setColor(Color.RED);
    raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
   }
   public RootRenderer.FramedArtwork discArtwork(){
    return new RootRenderer.FramedArtwork((scope,w,h)->{
     raster.graphics.setColor(Color.GREEN);
     raster.graphics.fill(new Rectangle2D.Float(0,0,w,h));
    },.5f,framing);
   }
  };
 }

 private static void paintSwatches(RootRasterTest.Raster raster,float width,float height){
  raster.graphics.setColor(new Color(0xff315d90,true));
  raster.graphics.fill(new Rectangle2D.Float(0,0,width,height));
  raster.graphics.setColor(new Color(0xffcf8c57,true));
  raster.graphics.fill(new Rectangle2D.Float(width*.33f,height*.55f,width*.2f,height*.2f));
 }

}
