package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;
import androidx.compose.ui.graphics.ImageBitmap;
public class RevealOrderTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static final class Cover {ImageBitmap bitmap;}
 public static void main(String[] args)throws Exception{
  check(!HostCoverBridge.imageReady(new Object()),"placeholder is not decoded artwork");
  Cover cover=new Cover();check(!HostCoverBridge.imageReady(cover),"empty image field is not ready");
  cover.bitmap=(ImageBitmap)Proxy.newProxyInstance(ImageBitmap.class.getClassLoader(),new Class[]{ImageBitmap.class},(p,m,a)->null);
  check(!HostCoverBridge.imageReady(cover),"resource bitmaps cannot prove actual album readiness");
  boolean shader=false;for(var f:Class.forName(HostCompatibility.current().painter()).getDeclaredFields())if(f.getType()==org.jetbrains.skia.Shader.class)shader=true;
  check(shader,"actual host mipmap painter matches readiness contract");
  Path root=Files.createTempDirectory("cd-reveal"),a=Files.createDirectory(root.resolve("A")),b=Files.createDirectory(root.resolve("B"));
  Path songA=Files.write(a.resolve("a.flac"),new byte[]{1}),songB=Files.write(b.resolve("b.flac"),new byte[]{1});
  Files.write(a.resolve("cd.png"),LocalDiscServiceTest.png(0xffff0000));Files.write(b.resolve("cd.png"),LocalDiscServiceTest.png(0xff00ff00));
  var song=new AtomicReference<>(songA.toString());var decoded=new AtomicInteger();var drawn=new AtomicInteger();
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  var service=new LocalDiscService<LocalDiscServiceTest.Asset>(song::get,(bytes,w,h)->{
   if(decoded.incrementAndGet()==2){entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("release timeout");}
   return new LocalDiscServiceTest.Asset(bytes);
  },()->{},s->{});
  check(!service.isResolved(song.get()),"group cannot take over before initial resolution");
  check(service.drawResolved(song.get(),x->drawn.incrementAndGet()),"initial pending consumes disc layer");check(drawn.get()==0,"pending draws no image");
  RootRasterTest.Raster raster=new RootRasterTest.Raster(630,420);
  RootRenderer.paint(raster.scope,1,raster::source,(scope,x,y,d)->service.drawResolved(song.get(),v->drawn.incrementAndGet()));
  check(raster.circles==0&&raster.sourceCalls==1,"pending renders complete cover and zero default rings");
  service.poll();check(service.isResolved(song.get()),"ready group may take over");check(service.drawResolved(song.get(),x->drawn.incrementAndGet())&&drawn.get()==1,"ready draws local");
  song.set(songB.toString());check(!service.isResolved(song.get()),"song change disables takeover before worker");check(service.drawResolved(song.get(),x->drawn.incrementAndGet())&&drawn.get()==1,"new song before worker has no old disc or silver fallback");
  Thread worker=new Thread(service::poll);worker.start();check(entered.await(5,TimeUnit.SECONDS),"slow decode entered");
  for(int i=0;i<100;i++)check(service.drawResolved(song.get(),x->{throw new AssertionError("old disc drawn");}),"pending must not return default");
  release.countDown();worker.join(5000);check(!worker.isAlive(),"worker done");
  check(service.drawResolved(song.get(),x->check(x.png.getRGB(32,12)==0xff00ff00,"new image")),"new ready");
  Files.delete(b.resolve("cd.png"));LocalDiscServiceTest.fresh(service);
  check(!service.drawResolved(song.get(),x->{}),"only confirmed absence returns default");
  RootRasterTest.Raster missing=new RootRasterTest.Raster(630,420);
  RootRenderer.paint(missing.scope,1,missing::source,(scope,x,y,d)->service.drawResolved(song.get(),v->{}),missing::path);
  check(missing.circles==11&&missing.sourceCalls==2,"confirmed missing generates cover disc with hub only");
  song.set(null);check(service.drawResolved(null,x->{throw new AssertionError();}),"temporary unavailable path never draws silver");
  service.close();System.out.println("PASS placeholder/empty cover rejected, actual host shader contract, pending cover-only render, 100 slow-decode frames without silver/old disc, confirmed missing fallback, unavailable path suppression");
  System.out.println("Offline integration test; native Windows transition timing is outside its scope.");
 }
}
