package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import javax.imageio.ImageIO;
public class TimedDiscMotionRasterTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  int frames=0;
  for(int amount:new int[]{0,25,50,100})for(boolean front:new boolean[]{false,true})for(boolean local:new boolean[]{false,true}){
   var m=new DiscTransition();long t=1_000_000_000L;m.trackChanged();m.trackReady(t);m.tick(t+350_000_000L);m.close(t+350_000_000L,()->{});
   for(int ms:new int[]{0,16,40,80,160,320,750}){
    m.tick(t+350_000_000L+ms*1_000_000L);var r=FrontRendererTest.render(amount,m.pose().exposed(),front,local,true);
    check(r.saved.isEmpty()&&r.graphics.getTransform().isIdentity()&&r.graphics.getClip()==null,"braking/retract raster restores drawing state");
    if(ms==750)check(m.finished()&&FrontRendererTest.equal(r.bitmap,FrontRendererTest.render(amount,0,front,local,true).bitmap),"retract ends with cover/reflection only");
    if(amount==50&&front&&local){Path path=Path.of("build/qa/v12-return-"+ms+".png");Files.createDirectories(path.getParent());ImageIO.write(r.bitmap,"png",path.toFile());}
    frames++;
   }
  }
  System.out.println("PASS "+frames+" timed production Renderer frames: 0/25/50/100, front/back, local/generated, brake/retract, reflected drawing state and clean final pixels");
 }
}
