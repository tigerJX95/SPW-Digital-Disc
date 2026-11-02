package io.github.tigerjx95.spwcdprobe;
import java.awt.*;
import java.awt.image.BufferedImage;
public class RotationRendererTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static RootRasterTest.Raster render(boolean front,boolean local,float angle)throws Exception{
  var r=new RootRasterTest.Raster(480,240);
  // Asymmetric local fixture with transparent center. Draw through the production bitmap API.
  var pixels=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);var g=pixels.createGraphics();
  g.setColor(Color.RED);g.fillArc(0,0,100,100,0,180);g.setColor(Color.BLUE);g.fillArc(0,0,100,100,180,180);
  g.setComposite(AlphaComposite.Clear);g.fillOval(44,44,12,12);g.dispose();
  var image=(androidx.compose.ui.graphics.ImageBitmap)java.lang.reflect.Proxy.newProxyInstance(androidx.compose.ui.graphics.ImageBitmap.class.getClassLoader(),new Class[]{androidx.compose.ui.graphics.ImageBitmap.class},(p,m,a)->switch(m.getName()){case "getWidth","getHeight"->100;default->throw new AssertionError(m);});
  r.images.put(image,pixels);
  RootRenderer.paint(r.scope,1,r::source,(scope,x,y,d)->{if(!local)return false;DiscImage.drawBitmap(scope,image,100,100,x,y,d);return true;},r::path,1,100,front,FrontRendererTest.layer(),angle);
  check(r.saved.isEmpty()&&r.graphics.getTransform().isIdentity()&&r.graphics.getClip()==null,"rotation restores canvas");return r;
 }
 public static void main(String[] args)throws Exception{
  for(boolean front:new boolean[]{false,true})for(boolean local:new boolean[]{false,true}){
   var zero=render(front,local,0);var half=render(front,local,180);
   int changed=0;
   for(int y=0;y<240;y++)for(int x=0;x<480;x++){
    if(x<240)check(zero.bitmap.getRGB(x,y)==half.bitmap.getRGB(x,y),"cover remains fixed");
    else if(zero.bitmap.getRGB(x,y)!=half.bitmap.getRGB(x,y))changed++;
   }
   check(changed>500,"only disc artwork changes orientation");
   check((half.bitmap.getRGB(360,125)>>>24)==(local?0:24),"center hole retains expected alpha after rotation");
   if(!local)check((half.bitmap.getRGB(360,120)>>>24)>0,"tiny white spindle retained");
  }
  for(boolean front:new boolean[]{false,true})for(boolean local:new boolean[]{false,true}){
   var upright=FrontRendererTest.render(50,1,front,local,true,0).bitmap;
   var turned=FrontRendererTest.render(50,1,front,local,true,90).bitmap;
   check(upright.getRGB(10,120)==turned.getRGB(10,120),"cover independent with reflection");
   if(!local){
    boolean discChanged=false,mirrorChanged=false;
    for(int y=0;y<turned.getHeight();y++)for(int x=240;x<360;x++)if(turned.getRGB(x,y)!=upright.getRGB(x,y)){if(y<240)discChanged=true;if(y>=244)mirrorChanged=true;}
    check(discChanged&&mirrorChanged,"generated disc and reflection rotate together");
   }
   check(turned.getRGB(300,242)==0,"reflection gap preserved");
  }
  System.out.println("PASS production rotation rasters: asymmetric local/generated, front/back, fixed cover, hole/spindle, reflection and restored transforms");
 }
}
