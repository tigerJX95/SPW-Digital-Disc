package io.github.tigerjx95.spwcdprobe;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
public class DiscMotionRasterTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  for(boolean local:new boolean[]{false,true})for(float progress:new float[]{0,.01f,.1f,.25f,.5f,.75f,1}){
   var r=new RootRasterTest.Raster(630,488);
   RootRenderer.Disc disc=(s,cx,cy,d)->{if(!local)return false;r.graphics.setColor(Color.MAGENTA);r.graphics.fill(new Ellipse2D.Float(cx-d/2,cy-d/2,d,d));return true;};
   RootRenderer.paint(r.scope,1,r::source,disc,r::path,progress);
   check(r.bitmap.getRGB(3,3)==Color.RED.getRGB()&&r.bitmap.getRGB(417,417)==Color.YELLOW.getRGB(),"full sleeve always preserved");
   if(progress==0){for(int y=0;y<420;y++)for(int x=420;x<630;x++)check(r.bitmap.getRGB(x,y)==0,"no disc escapes before cover settles");}
   int end=Math.min(629,(int)Math.ceil(420*(.99f+.5f*progress))+1);
   for(int y=0;y<420;y++)for(int x=Math.max(420,end);x<630;x++)check(r.bitmap.getRGB(x,y)==0,"moving edge never reaches canvas border");
   check(r.saved.isEmpty()&&r.graphics.getTransform().isIdentity()&&r.graphics.getClip()==null,"clip and transform restored on each frame");
  }
  System.out.println("PASS 14 production renderer motion rasters: local/generated disc, full sleeve, initially hidden, moving circular edge, canvas boundary, restored clip/transform");
 }
}
