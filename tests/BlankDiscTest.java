package io.github.tigerjx95.spwcdprobe;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import javax.imageio.ImageIO;
import java.io.File;
public class BlankDiscTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static RootRasterTest.Raster render(int side,boolean front,float angle,boolean custom)throws Exception{
  var r=new RootRasterTest.Raster(side*2,side);
  RootRenderer.Source placeholder=new RootRenderer.Source(){
   public void draw(androidx.compose.ui.graphics.drawscope.DrawScope s,float w,float h){r.graphics.setColor(new Color(0xff514c49,true));r.graphics.fill(new Rectangle2D.Float(0,0,w,h));}
   public boolean blankDisc(){return true;}
  };
  RootRenderer.paint(r.scope,1,placeholder,(s,x,y,d)->custom,r::path,1,100,front,FrontRendererTest.layer(),angle);
  check(r.saved.isEmpty()&&r.graphics.getTransform().isIdentity(),"transforms restored");
  return r;
 }
 public static void main(String[] args)throws Exception{
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
  ImageIO.write(preview.bitmap,"png",new File(args[0]));
  System.out.println("PASS blank disc: shared finish, untouched placeholder, translucent hole, rotation/layers and custom priority");
 }
}
