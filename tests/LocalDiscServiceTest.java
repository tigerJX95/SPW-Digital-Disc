package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
public class LocalDiscServiceTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Asset implements AutoCloseable {
  final BufferedImage png;int closed;
  Asset(byte[] bytes)throws Exception{png=ImageIO.read(new ByteArrayInputStream(bytes));}
  public void close(){closed++;}
 }
 static void fresh(LocalDiscService<?> service)throws Exception{var field=LocalDiscService.class.getDeclaredField("nextScan");field.setAccessible(true);field.setLong(service,0);service.poll();}
 static byte[] png(int color)throws Exception{
  BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<64;y++)for(int x=0;x<64;x++){double r=Math.hypot(x-31.5,y-31.5);if(r>8&&r<30)image.setRGB(x,y,color);}
  ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
 }
 static Asset get(LocalDiscService<Asset> service,String song)throws Exception{AtomicReference<Asset> value=new AtomicReference<>();service.drawFor(song,value::set);return value.get();}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("reborn-local-cd-test"),one=Files.createDirectories(root.resolve("专辑一")),two=Files.createDirectories(root.resolve("album-two"));
  Path track=Files.write(one.resolve("曲目.flac"),new byte[]{1}),track2=Files.write(two.resolve("track.flac"),new byte[]{1});
  AtomicReference<String> song=new AtomicReference<>(track.toString());AtomicInteger revisions=new AtomicInteger();
  List<Asset> decoded=new ArrayList<>();
  var service=new LocalDiscService<Asset>(song::get,(bytes,w,h)->{Asset a=new Asset(bytes);decoded.add(a);return a;},revisions::incrementAndGet,s->{});
  fresh(service);check(get(service,song.get())==null,"missing artwork uses default");
  Files.write(one.resolve("disc.jpg"),png(0xff00ff00));Files.write(one.resolve("disc.png"),png(0xff0000ff));Files.write(one.resolve("cd.png"),png(0xffff0000));
  fresh(service);Asset red=get(service,song.get());check(red!=null&&red.png.getRGB(32,12)==0xffff0000,"cd.png has first priority");
  check(red.png.getRGB(32,32)==0&&red.png.getRGB(0,0)==0,"transparent hole and background retained");
  fresh(service);check(get(service,song.get())==red&&decoded.size()==1,"unchanged file reuses decoded image");
  Files.write(one.resolve("cd.png"),new byte[]{1,2,3});fresh(service);Asset blue=get(service,song.get());
  check(blue.png.getRGB(32,12)==0xff0000ff&&red.closed==1,"bad primary tries disc.png and disposes old asset");
  Files.delete(one.resolve("disc.png"));fresh(service);check(get(service,song.get()).png.getRGB(32,12)==0xff00ff00,"third filename fallback");
  song.set(track2.toString());check(get(service,song.get())==null,"old CD never appears for next song before worker updates");
  fresh(service);check(get(service,song.get())==null,"next album without image uses default");
  Files.write(two.resolve("cd.png"),png(0xffffff00));fresh(service);Asset yellow=get(service,song.get());check(yellow.png.getRGB(32,12)==0xffffff00,"new folder loads independently");
  song.set(track2.toUri().toString());fresh(service);check(get(service,song.get())!=null,"file URI supported");
  song.set("https://example.invalid/song.flac");fresh(service);check(get(service,song.get())==null,"online source does not borrow a local CD");
  service.close();for(Asset a:decoded)check(a.closed==1,"each retired asset closed exactly once");
  byte[] bomb=png(0xff000000);bomb[16]=0x7f;bomb[17]=(byte)0xff;bomb[18]=(byte)0xff;bomb[19]=(byte)0xff;
  try{LocalDiscService.dimensions(bomb);throw new AssertionError("oversized PNG accepted");}catch(IOException expected){}
  // Slow decoding must not publish an old song after the playback source changes.
  song.set(track2.toString());CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Asset> raced=new AtomicReference<>();
  var race=new LocalDiscService<Asset>(song::get,(bytes,w,h)->{Asset a=new Asset(bytes);raced.set(a);entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("release timeout");return a;},()->{},s->{});
  Thread t=new Thread(race::poll);t.start();check(entered.await(5,TimeUnit.SECONDS),"decode reached background section");song.set(track.toString());release.countDown();t.join(5000);
  check(!t.isAlive()&&raced.get().closed==1&&get(race,song.get())==null,"stale decode discarded and closed");race.close();
  song.set(track2.toString());CountDownLatch closingEntered=new CountDownLatch(1),closingRelease=new CountDownLatch(1);AtomicReference<Asset> closingAsset=new AtomicReference<>();
  var closing=new LocalDiscService<Asset>(song::get,(bytes,w,h)->{Asset a=new Asset(bytes);closingAsset.set(a);closingEntered.countDown();if(!closingRelease.await(5,TimeUnit.SECONDS))throw new AssertionError("close release timeout");return a;},()->{},s->{});
  Thread pending=new Thread(closing::poll);pending.start();check(closingEntered.await(5,TimeUnit.SECONDS),"pending image decode entered");closing.close();closingRelease.countDown();pending.join(5000);
  check(!pending.isAlive()&&closingAsset.get().closed==1&&get(closing,song.get())==null,"close while decoding cannot publish or leak a late image");
  System.out.println("PASS local PNG lookup: PNG/JPEG priority, corrupt/missing fallback, transparency, unchanged cache, Unicode paths, file URI, cross-track isolation, stale decode rejection, close-during-load and exact disposal");
  System.out.println("Skia native decode is not executed by this ImageIO-backed decoder test.");
 }
}
