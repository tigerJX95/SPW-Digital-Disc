package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.graphics.Canvas;
import androidx.compose.ui.graphics.ColorKt;
import androidx.compose.ui.graphics.drawscope.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
public class RootRasterTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Raster {
  final BufferedImage bitmap;Graphics2D graphics;final float width,height;
  record Saved(AffineTransform transform,java.awt.Shape clip,Graphics2D parent,BufferedImage layer,float alpha){}
  final ArrayDeque<Saved> saved=new ArrayDeque<>();int circles,sourceCalls;
  final Map<androidx.compose.ui.graphics.Path,java.awt.geom.Area> masks=new IdentityHashMap<>();
  androidx.compose.ui.graphics.Path path(){
   final java.awt.geom.Area area=new java.awt.geom.Area();
   var result=(androidx.compose.ui.graphics.Path)Proxy.newProxyInstance(androidx.compose.ui.graphics.Path.class.getClassLoader(),new Class[]{androidx.compose.ui.graphics.Path.class},(p,m,a)->{
    if(m.getName().equals("addOval")){
     var r=(androidx.compose.ui.geometry.Rect)a[0];area.add(new java.awt.geom.Area(new Ellipse2D.Float(r.getLeft(),r.getTop(),r.getWidth(),r.getHeight())));return null;
    }
    throw new AssertionError(m);
   });
   masks.put(result,area);return result;
  }
  final Method toArgb;
  final Map<androidx.compose.ui.graphics.ImageBitmap,BufferedImage> images=new IdentityHashMap<>();
  final Canvas canvas;
  final DrawContext context;
  final DrawScope scope;
  Raster(int w,int h)throws Exception{
   width=w;height=h;bitmap=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);graphics=bitmap.createGraphics();
   toArgb=ColorKt.class.getMethod("toArgb-8_81llA",long.class);
   canvas=(Canvas)Proxy.newProxyInstance(Canvas.class.getClassLoader(),new Class[]{Canvas.class},(p,m,a)->{
    switch(m.getName()){
     case "save"->saved.push(new Saved(graphics.getTransform(),graphics.getClip(),null,null,1));
     case "saveLayer"->{
      var image=new BufferedImage((int)width,(int)height,BufferedImage.TYPE_INT_ARGB);
      var parent=graphics;saved.push(new Saved(parent.getTransform(),parent.getClip(),parent,image,((androidx.compose.ui.graphics.Paint)a[1]).getAlpha()));
      graphics=image.createGraphics();graphics.setTransform(parent.getTransform());graphics.setClip(parent.getClip());
     }
     case "restore"->{var state=saved.pop();
      if(state.parent()!=null){
       graphics.dispose();graphics=state.parent();var output=(Graphics2D)graphics.create();output.setTransform(new AffineTransform());
       if(state.clip()!=null)output.setClip(state.transform().createTransformedShape(state.clip()));
       output.setComposite(java.awt.AlphaComposite.SrcOver.derive(state.alpha()));output.drawImage(state.layer(),0,0,null);output.dispose();
      }else{graphics.setTransform(state.transform());graphics.setClip(state.clip());}
     }
     case "translate"->graphics.translate((Float)a[0],(Float)a[1]);
     case "rotate"->graphics.rotate(Math.toRadians((Float)a[0]));
     case "scale"->graphics.scale((Float)a[0],(Float)a[1]);
     case "clipRect-N_I0leg"->graphics.clip(new Rectangle2D.Float((Float)a[0],(Float)a[1],(Float)a[2]-(Float)a[0],(Float)a[3]-(Float)a[1]));
     case "clipPath-mtrdD-E"->{
      var area=masks.get(a[0]);if(area==null)throw new AssertionError("unknown mask");
      if((Integer)a[1]==1)graphics.clip(area);
      else{var clip=new java.awt.geom.Area(graphics.getClip());clip.subtract(area);graphics.setClip(clip);}
     }
     default->throw new AssertionError("unexpected Canvas API "+m);
    }return null;
   });
   context=(DrawContext)Proxy.newProxyInstance(DrawContext.class.getClassLoader(),new Class[]{DrawContext.class},(p,m,a)->{
    if(m.getName().equals("getCanvas"))return canvas;throw new AssertionError(m);
   });
   scope=(DrawScope)Proxy.newProxyInstance(DrawScope.class.getClassLoader(),new Class[]{DrawScope.class},(p,m,a)->{
    switch(m.getName()){
     case "getSize-NH-jbRc"->{return RootRenderer.pack(width,height);}
     case "getDrawContext"->{return context;}
     case "drawRect-AsUm42w"->{
      if((Integer)a[6]!=6||a[4]!=androidx.compose.ui.graphics.drawscope.Fill.INSTANCE)throw new AssertionError("reflection mask uses non-null Fill and DstIn");
      var brush=(androidx.compose.ui.graphics.LinearGradient)a[0];
      long start=(Long)brush.getClass().getMethod("getStart-F1C5BW0$ui_graphics").invoke(brush),end=(Long)brush.getClass().getMethod("getEnd-F1C5BW0$ui_graphics").invoke(brush);
      var unbox=androidx.compose.ui.graphics.Color.class.getMethod("unbox-impl");
      var colors=brush.getColors$ui_graphics();int first=(Integer)toArgb.invoke(null,unbox.invoke(colors.get(0))),last=(Integer)toArgb.invoke(null,unbox.invoke(colors.get(1)));
      var composite=graphics.getComposite();var paint=graphics.getPaint();graphics.setComposite(java.awt.AlphaComposite.DstIn);
      graphics.setPaint(new java.awt.GradientPaint(0,RootRenderer.y(start),new Color(first,true),0,RootRenderer.y(end),new Color(last,true)));
      long pos=(Long)a[1],size=(Long)a[2];graphics.fill(new Rectangle2D.Float(RootRenderer.x(pos),RootRenderer.y(pos),RootRenderer.x(size),RootRenderer.y(size)));
      graphics.setComposite(composite);graphics.setPaint(paint);return null;
     }
     case "drawImage-9jGpkUE"->{
      if(a[6]!=androidx.compose.ui.graphics.drawscope.Fill.INSTANCE)throw new AssertionError("DrawStyle must be non-null Fill, matching actual host");
      BufferedImage image=images.get(a[0]);if(image==null)throw new AssertionError("unknown image");
      long so=(Long)a[1],ss=(Long)a[2],dest=(Long)a[3],ds=(Long)a[4];
      int sx=(int)(so>>>32),sy=(int)so,sw=(int)(ss>>>32),sh=(int)ss,dx=(int)(dest>>>32),dy=(int)dest,dw=(int)(ds>>>32),dh=(int)ds;
      graphics.drawImage(image,dx,dy,dx+dw,dy+dh,sx,sy,sx+sw,sy+sh,null);return null;
     }
     case "drawCircle-V9BoPsw"->{
      circles++;
      if(a[0] instanceof androidx.compose.ui.graphics.LinearGradient brush){
       long start=(Long)brush.getClass().getMethod("getStart-F1C5BW0$ui_graphics").invoke(brush),end=(Long)brush.getClass().getMethod("getEnd-F1C5BW0$ui_graphics").invoke(brush);
       var stops=brush.getStops$ui_graphics();var colors=brush.getColors$ui_graphics();
       float[] fractions=new float[stops.size()];Color[] palette=new Color[colors.size()];
       var unbox=androidx.compose.ui.graphics.Color.class.getMethod("unbox-impl");
       for(int i=0;i<stops.size();i++){fractions[i]=stops.get(i);palette[i]=new Color((Integer)toArgb.invoke(null,unbox.invoke(colors.get(i))),true);}
       var previous=graphics.getPaint();var previousStroke=graphics.getStroke();
       graphics.setPaint(new LinearGradientPaint(RootRenderer.x(start),RootRenderer.y(start),RootRenderer.x(end),RootRenderer.y(end),fractions,palette));
       long at=(Long)a[2];float drawn=(Float)a[1];
       var shape=new Ellipse2D.Float(RootRenderer.x(at)-drawn,RootRenderer.y(at)-drawn,drawn*2,drawn*2);
       if(a[4]==Fill.INSTANCE)graphics.fill(shape);
       else{graphics.setStroke(new BasicStroke(((androidx.compose.ui.graphics.drawscope.Stroke)a[4]).getWidth()));graphics.draw(shape);}
       graphics.setPaint(previous);graphics.setStroke(previousStroke);return null;
      }
      if(a[4]!=Fill.INSTANCE||(Integer)a[6]!=3)throw new AssertionError("radial hub requires Fill and SrcOver");
      var brush=(androidx.compose.ui.graphics.RadialGradient)a[0];
      long center=(Long)brush.getClass().getMethod("getCenter-F1C5BW0$ui_graphics").invoke(brush);
      float radius=brush.getRadius$ui_graphics();var stops=brush.getStops$ui_graphics();var colors=brush.getColors$ui_graphics();
      float[] fractions=new float[stops.size()];Color[] palette=new Color[colors.size()];
      var unbox=androidx.compose.ui.graphics.Color.class.getMethod("unbox-impl");
      for(int i=0;i<stops.size();i++){fractions[i]=stops.get(i);palette[i]=new Color((Integer)toArgb.invoke(null,unbox.invoke(colors.get(i))),true);}
      var previous=graphics.getPaint();
      graphics.setPaint(new RadialGradientPaint(RootRenderer.x(center),RootRenderer.y(center),radius,fractions,palette));
      long at=(Long)a[2];float drawn=(Float)a[1];graphics.fill(new Ellipse2D.Float(RootRenderer.x(at)-drawn,RootRenderer.y(at)-drawn,drawn*2,drawn*2));
      graphics.setPaint(previous);return null;
     }
     case "drawCircle-VaOC9Bg"->{
      circles++;int color=(Integer)toArgb.invoke(null,(Long)a[0]);float radius=(Float)a[1];long pos=(Long)a[2];
      graphics.setColor(new Color(color,true));
      var circle=new Ellipse2D.Float(RootRenderer.x(pos)-radius,RootRenderer.y(pos)-radius,2*radius,2*radius);
      if(a[4] instanceof androidx.compose.ui.graphics.drawscope.Stroke stroke){graphics.setStroke(new BasicStroke(Math.max(1,stroke.getWidth())));graphics.draw(circle);}else graphics.fill(circle);return null;
     }
     default->throw new AssertionError("unexpected DrawScope API "+m);
    }
   });
  }
  void source(DrawScope scope,float w,float h){
   sourceCalls++;
   graphics.setColor(Color.WHITE);graphics.fill(new Rectangle2D.Float(0,0,w,h));
   graphics.setColor(Color.RED);graphics.fill(new Rectangle2D.Float(0,0,w/4,h/4));
   graphics.setColor(Color.GREEN);graphics.fill(new Rectangle2D.Float(w*3/4,0,w/4,h/4));
   graphics.setColor(Color.BLUE);graphics.fill(new Rectangle2D.Float(0,h*3/4,w/4,h/4));
   graphics.setColor(Color.YELLOW);graphics.fill(new Rectangle2D.Float(w*3/4,h*3/4,w/4,h/4));
  }
 }
 public static void main(String[] args)throws Exception{
  int cases=0;
  for(int side:new int[]{180,295,420,620}){
   Raster r=new Raster(side*3/2,side);RootRenderer.paint(r.scope,1,r::source,(s,x,y,d)->false,r::path);
   check(r.sourceCalls==2&&r.circles==9,"cover copy generates CD with white rim, translucent bevel and spindle dot");
   int p=5;
   check(r.bitmap.getRGB(p,p)==Color.RED.getRGB(),"full cover upper-left preserved");
   check(r.bitmap.getRGB(side-p,p)==Color.GREEN.getRGB(),"full cover upper-right preserved");
   check(r.bitmap.getRGB(p,side-p)==Color.BLUE.getRGB(),"full cover lower-left preserved");
   check(r.bitmap.getRGB(side-p,side-p)==Color.YELLOW.getRGB(),"full cover lower-right preserved");
   int discEnd=Math.round(side*1.48f);
   check((r.bitmap.getRGB(discEnd-3,side/2)>>>24)!=0,"CD reaches its circular right edge beyond source square");
   check((r.bitmap.getRGB(r.bitmap.getWidth()-1,side/2)>>>24)==0,"disc/shadow do not reach canvas clipping border");
   check(r.saved.isEmpty(),"Canvas restored");
   if(side==420){Path path=Path.of(args[0]);Files.createDirectories(path.getParent());ImageIO.write(r.bitmap,"png",path.toFile());}
   cases++;
  }
  for(float[] window:new float[][]{{1673,930},{940,600},{1920,1080},{2560,1440}})for(float density:new float[]{1,1.25f,1.5f})for(boolean secondary:new boolean[]{false,true}){
   SelfDrawLayout p=SelfDrawLayout.fit(window[0],window[1],density,secondary);
   check(Math.abs(p.width()-p.side()*1.5f)<.01f,"actual widget wider than complete cover");
   check(p.left()>=0&&p.left()+p.width()<=window[0],"group fits viewport width");
   check(p.top()>=0&&p.top()+p.height()<=window[1]-90*density,"playback controls outside widget");
   cases++;
  }
  SelfDrawLayout first=SelfDrawLayout.fit(1673,930,1,false),second=SelfDrawLayout.fit(1673,930,1,true);
  check(second.side()>first.side(),"secondary cover larger than primary");
  System.out.println("PASS "+cases+" raster/layout cases: all four source corners, circular CD edge beyond old square, Canvas width=1.5 cover widths, bounds and controls");
  System.out.println("Actual production RootRenderer executed through a Java2D DrawScope adapter. Not a Windows SPW screenshot.");
 }
}
