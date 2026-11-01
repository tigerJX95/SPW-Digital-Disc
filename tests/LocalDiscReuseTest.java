package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.*;
/** Counts actual decode calls and checks identity, isolation and exact disposal. */
public class LocalDiscReuseTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("disc-reuse"),a=Files.createDirectory(root.resolve("A")),b=Files.createDirectory(root.resolve("B"));
  String a1=Files.write(a.resolve("1.flac"),new byte[]{1}).toString(),a2=Files.write(a.resolve("2.flac"),new byte[]{1}).toString();
  String b1=Files.write(b.resolve("1.flac"),new byte[]{1}).toString();
  Path lower=a.resolve("disc.png"),higher=a.resolve("cd.png");
  byte[] red=LocalDiscServiceTest.png(0xffff0000),green=LocalDiscServiceTest.png(0xff00ff00);
  Files.write(lower,red);var song=new AtomicReference<>(a1);var decodes=new AtomicInteger();
  var assets=new ArrayList<LocalDiscServiceTest.Asset>();
  var service=new LocalDiscService<LocalDiscServiceTest.Asset>(song::get,(bytes,w,h)->{
   decodes.incrementAndGet();var result=new LocalDiscServiceTest.Asset(bytes);assets.add(result);return result;
  },()->{},s->{});
  service.poll();var first=LocalDiscServiceTest.get(service,a1);
  check(first!=null&&decodes.get()==1,"initial decode");
  for(int i=0;i<20;i++){
   song.set(i%2==0?a2:a1);service.request(song.get());
   check(!service.isResolved(song.get())&&LocalDiscServiceTest.get(service,song.get())==null,"retained image is unpublished before ordered revalidation");
   AtomicInteger draws=new AtomicInteger();check(service.drawResolved(song.get(),v->draws.incrementAndGet())&&draws.get()==0,"pending cannot expose retained image");
   service.poll();check(LocalDiscServiceTest.get(service,song.get())==first&&first.closed==0,"same album reuses exact decoded asset");
  }
  check(decodes.get()==1,"20 same-directory switches require only the first decode");
  // A new corrupt primary must be tried, then the unchanged lower candidate reused.
  Files.write(higher,new byte[]{1});song.set(a2);service.request(a2);service.poll();
  check(LocalDiscServiceTest.get(service,a2)==first&&decodes.get()==1,"corrupt higher-priority file does not invalidate usable lower cache");
  Files.write(higher,green);song.set(a1);service.request(a1);service.poll();
  var primary=LocalDiscServiceTest.get(service,a1);
  check(primary!=first&&primary.png.getRGB(32,12)==0xff00ff00&&first.closed==1&&decodes.get()==2,"new valid higher priority outranks cached lower image");
  Files.write(higher,red);Files.setLastModifiedTime(higher,FileTime.fromMillis(Files.getLastModifiedTime(higher).toMillis()+2000));
  song.set(a2);service.request(a2);service.poll();var replaced=LocalDiscServiceTest.get(service,a2);
  check(replaced!=primary&&primary.closed==1&&replaced.png.getRGB(32,12)==0xffff0000,"changed file reloads and closes old cache");
  // Same bytes/size/time in another directory still must not match the key.
  Path other=b.resolve("cd.png");Files.write(other,Files.readAllBytes(higher));Files.setLastModifiedTime(other,Files.getLastModifiedTime(higher));
  int before=decodes.get();song.set(b1);service.request(b1);service.poll();var cross=LocalDiscServiceTest.get(service,b1);
  check(cross!=replaced&&replaced.closed==1&&decodes.get()==before+1,"path is part of cache identity, even with equal size/time");
  // Rapid B -> A -> B may reuse a published B only after latest B revalidation.
  song.set(a1);service.request(a1);song.set(b1);service.request(b1);
  check(!service.isResolved(b1)&&LocalDiscServiceTest.get(service,b1)==null,"ABA does not immediately publish retained pixels");
  service.poll();check(LocalDiscServiceTest.get(service,b1)==cross&&decodes.get()==before+1,"latest verified B can reuse previously published B");
  Files.delete(other);song.set(a1);service.request(a1);song.set(b1);service.request(b1);service.poll();
  check(LocalDiscServiceTest.get(service,b1)==null&&cross.closed==1,"deleted cached file uses default and releases pixels");
  service.close();for(var asset:assets)check(asset.closed==1,"every published/retained asset closed exactly once");
  // A file changed by the decoder must not publish pixels under its old stamp.
  song.set(a1);var raced=new AtomicReference<LocalDiscServiceTest.Asset>();var attempts=new AtomicInteger();
  var race=new LocalDiscService<LocalDiscServiceTest.Asset>(song::get,(bytes,w,h)->{
   var asset=new LocalDiscServiceTest.Asset(bytes);
   if(attempts.incrementAndGet()==1){raced.set(asset);Files.write(higher,green);Files.setLastModifiedTime(higher,FileTime.fromMillis(Files.getLastModifiedTime(higher).toMillis()+3000));}
   return asset;
  },()->{},s->{});
  Files.delete(lower);race.poll();check(raced.get().closed==1&&LocalDiscServiceTest.get(race,a1)==null,"replacement during decode disposes unpublishable pixels");
  LocalDiscServiceTest.fresh(race);check(LocalDiscServiceTest.get(race,a1).png.getRGB(32,12)==0xff00ff00,"next lookup loads stable replacement");
  song.set(a2);race.request(a2);var retained=LocalDiscServiceTest.get(race,a2);check(retained==null,"pending ownership is private");
  race.close();
  // Null media releases a retained candidate immediately, without needing worker I/O.
  song.set(a1);var stoppedAsset=new AtomicReference<LocalDiscServiceTest.Asset>();
  var stopped=new LocalDiscService<LocalDiscServiceTest.Asset>(song::get,(bytes,w,h)->{var v=new LocalDiscServiceTest.Asset(bytes);stoppedAsset.set(v);return v;},()->{},s->{});
  stopped.poll();song.set(a2);stopped.request(a2);song.set(null);stopped.request(null);
  check(stoppedAsset.get().closed==1,"no-media request releases unpublished candidate");stopped.close();
  System.out.println("PASS 20 same-album switches with one decode, unpublished pending cache, priority insertion/corruption, replacement invalidation, cross-directory isolation, verified cached ABA, deletion, decode-time replacement and exact cleanup");
 }
}
