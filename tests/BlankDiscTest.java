package io.github.tigerjx95.spwcdprobe;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import javax.imageio.ImageIO;
import java.io.File;
public class BlankDiscTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static RootRasterTest.Raster render(int side,boolean front,float angle,boolean custom)throws Exception{
  return render(side,front,angle,custom,DiscSettings.Style.CD);
 }
 static RootRasterTest.Raster render(int side,boolean front,float angle,boolean custom,DiscSettings.Style style)throws Exception{
  var r=new RootRasterTest.Raster(side*2,side);
  RootRenderer.Source placeholder=new RootRenderer.Source(){
   public void draw(androidx.compose.ui.graphics.drawscope.DrawScope s,float w,float h){r.graphics.setColor(new Color(0xff514c49,true));r.graphics.fill(new Rectangle2D.Float(0,0,w,h));}
   public boolean blankDisc(){return true;}
  };
  RootRenderer.paint(r.scope,1,placeholder,(s,x,y,d)->custom,r::path,1,100,front,FrontRendererTest.layer(),angle,style,DiscSettings.VinylColor.BLUE,false);
  check(r.saved.isEmpty()&&r.graphics.getTransform().isIdentity(),"transforms restored");
  return r;
 }
 public static void main(String[] args)throws Exception{
  for(boolean front:new boolean[]{false,true}){
   int side=240;
   var expected=render(side,front,37,false,DiscSettings.Style.CD).bitmap;
   var expectedPixels=expected.getRGB(0,0,side*2,side,null,0,side*2);
   for(var style:new DiscSettings.Style[]{DiscSettings.Style.COLORED_VINYL,DiscSettings.Style.BLACK_VINYL,
       DiscSettings.Style.REEL,DiscSettings.Style.REEL_COVER}){
    var actual=render(side,front,37,false,style).bitmap;
    check(java.util.Arrays.equals(expectedPixels,actual.getRGB(0,0,side*2,side,null,0,side*2)),
        "confirmed missing cover must draw a silver CD for "+style+" front="+front);
    check(render(side,front,37,true,style).circles==0,"custom disc takes priority over blank fallback for "+style);
   }
  }
  for(int side:new int[]{180,420,620})for(boolean front:new boolean[]{false,true})for(float angle:new float[]{0,37,180}){
   var r=render(side,front,angle,false);
   check(r.bitmap.getRGB(side/2,side/2)==0xff514c49,"native placeholder cover unchanged");
   int x=Math.round(side*1.5f),y=side/2;float radius=side*.49f;
   check((r.bitmap.getRGB(x+Math.round(radius*.075f),y)>>>24)==24,"same translucent hole");
   int face=r.bitmap.getRGB(x+Math.round(radius*.5f),y);
   check((face>>>24)==255&&(face>>16&255)>195&&(face>>8&255)>195&&(face&255)>195,"silver white face");
   check(r.circles==10,"one face plus existing nine finish circles");
   check(r.bitmap.getRGB(side*2-1,y)==0,"same disc bounds");
   var local=render(side,front,angle,true);check(local.circles==0,"custom artwork bypasses silver and finish");
  }
  var preview=render(620,false,0,false);
  int center=930, y=310;
  float radius=620*.49f;
  int innerSilver=preview.bitmap.getRGB(center+Math.round(radius*.31f),y)>>8&255;
  int midSilver=preview.bitmap.getRGB(center+Math.round(radius*.52f),y)>>8&255;
  int outerSilver=preview.bitmap.getRGB(center+Math.round(radius*.77f),y)>>8&255;
  check(innerSilver>midSilver+6&&midSilver>outerSilver+6,"silver face has a gradual radial falloff");
  ImageIO.write(preview.bitmap,"png",new File(args[0]));
  System.out.println("PASS blank disc: shared finish, untouched placeholder, translucent hole, rotation/layers and custom priority");
 }
}
