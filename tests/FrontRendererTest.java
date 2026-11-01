package io.github.tigerjx95.spwcdprobe;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.Paint;
public class FrontRendererTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static final int SIDE=240;
 static RootRasterTest.Raster render(int amount,float progress,boolean front,boolean local,boolean mirror)throws Exception{
  return render(amount,progress,front,local,mirror,0f);
 }
 static RootRasterTest.Raster render(int amount,float progress,boolean front,boolean local,boolean mirror,float angle)throws Exception{
  int width=Math.round(SIDE*DiscExposure.widthFactor(amount));var r=new RootRasterTest.Raster(width,mirror?304:240);
  BufferedImage png=ImageIO.read(new ByteArrayInputStream(LocalDiscServiceTest.png(0xffc93659)));
  ImageBitmap image=(ImageBitmap)Proxy.newProxyInstance(ImageBitmap.class.getClassLoader(),new Class[]{ImageBitmap.class},(p,m,a)->switch(m.getName()){
   case "getWidth"->64;case "getHeight"->64;default->throw new AssertionError(m);
  });r.images.put(image,png);
  RootRenderer.Disc disc=(scope,cx,cy,d)->{if(local){DiscImage.drawBitmap(scope,image,64,64,cx,cy,d);return true;}return false;};
  FrontDiscLayer frontLayer=layer();
  RootRenderer.paint(r.scope,1,r::source,disc,r::path,progress,amount,front,frontLayer,angle);
  if(mirror){
   float[] alpha={1};Paint paint=(Paint)Proxy.newProxyInstance(Paint.class.getClassLoader(),new Class[]{Paint.class},(p,m,a)->switch(m.getName()){
    case "setAlpha"->{alpha[0]=(Float)a[0];yield null;}case "getAlpha"->alpha[0];default->throw new AssertionError(m);
   });
   new ReflectionRenderer(()->paint).draw(r.scope,SIDE,width,60,4,()->RootRenderer.paint(r.scope,1,r::source,disc,r::path,progress,amount,front,frontLayer,angle));
  }
  check(r.saved.isEmpty()&&r.graphics.getClip()==null&&r.graphics.getTransform().isIdentity(),"all front-mode transforms/clips restored");return r;
 }
 static FrontDiscLayer layer(){
  float[] alpha={1};Paint paint=(Paint)Proxy.newProxyInstance(Paint.class.getClassLoader(),new Class[]{Paint.class},(p,m,a)->switch(m.getName()){
   case "setAlpha"->{alpha[0]=(Float)a[0];yield null;}case "getAlpha"->alpha[0];default->throw new AssertionError(m);
  });return new FrontDiscLayer(()->paint);
 }
 static boolean equal(BufferedImage a,BufferedImage b){
  if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;
  for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return false;
  return true;
 }
 public static void main(String[] args)throws Exception{
  for(boolean local:new boolean[]{false,true}){
   for(int amount:new int[]{0,25,50,75,100}){
    var back=render(amount,1,false,local,false);var front=render(amount,1,true,local,false);
    if(amount==0||amount==100)check(equal(back.bitmap,front.bitmap),"zero hides CD; full travel has no layer overlap");
    if(amount==50&&local){
     check(front.bitmap.getRGB(180,120)!=back.bitmap.getRGB(180,120),"front pixels cover sleeve in overlap");
     check(front.bitmap.getRGB(10,120)==back.bitmap.getRGB(10,120),"uncovered sleeve pixels unchanged");
    }
    if(amount==25&&local)check(front.bitmap.getRGB(180,120)==back.bitmap.getRGB(180,120),"transparent disc hole reveals underlying sleeve");
    Path output=Path.of("build/qa/front-"+amount+(local?"-local":"-generated")+".png");Files.createDirectories(output.getParent());ImageIO.write(front.bitmap,"png",output.toFile());
    check(equal(render(amount,0,false,local,false).bitmap,render(amount,0,true,local,false).bitmap),"no front-disc residue at zero motion progress");
   }
  }
  var empty=render(50,0,true,true,false);
  for(int i=1;i<=20;i++){
   float progress=i/20f;var frame=render(50,progress,true,true,false);
   check(frame.sourceCalls==1,"whole local disc remains independent of full cover");
  }
  var entry=render(50,.1f,true,true,false);
  check(entry.bitmap.getRGB(96,120)!=empty.bitmap.getRGB(96,120),"left half of moving circle is present before old wipe edge reaches it");
  check(entry.bitmap.getRGB(96,120)==entry.bitmap.getRGB(208,120),"equal-color disc regions on both sides have uniform entry opacity");
  var mirrored=render(50,1,true,true,true);var backMirror=render(50,1,false,true,true);
  check(mirrored.bitmap.getRGB(180,284)!=backMirror.bitmap.getRGB(180,284),"reflection uses the same front/back ordering in overlap");
  check(mirrored.bitmap.getRGB(10,242)==0,"reflection gap stays transparent");
  DiscTransition motion=new DiscTransition();long t=1_000_000_000L;motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  AtomicInteger returns=new AtomicInteger();motion.close(t+950_000_000L,returns::incrementAndGet,true);motion.tick(t+1_600_000_000L);
  check(returns.get()==1&&motion.finished()&&equal(empty.bitmap,render(50,motion.pose().exposed(),true,true,false).bitmap),"normal retract finishes once without front-disc residue");
  System.out.println("PASS front/back production rasters: local/generated 0/25/50/75/100, opaque overlap, transparent hole over sleeve, zero/full equivalence, 20 whole-disc motion frames, matching reflection order and retract cleanup");
 }
}
