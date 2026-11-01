package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** A -> B -> A while A is decoding must not publish the first A request. */
public class DiscGenerationTest {
 public static void main(String[] args)throws Exception{
  Path folder=Files.createTempDirectory("disc-generation");
  String a=Files.write(folder.resolve("a.flac"),new byte[]{1}).toString();
  String b=Files.write(folder.resolve("b.flac"),new byte[]{1}).toString();
  Files.write(folder.resolve("cd.png"),LocalDiscServiceTest.png(0xffff0000));
  AtomicReference<String> song=new AtomicReference<>(a);
  AtomicReference<LocalDiscServiceTest.Asset> old=new AtomicReference<>();
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  AtomicInteger count=new AtomicInteger();
  var service=new LocalDiscService<LocalDiscServiceTest.Asset>(song::get,(bytes,w,h)->{
   var asset=new LocalDiscServiceTest.Asset(bytes);
   if(count.incrementAndGet()==1){old.set(asset);entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("release timeout");}
   return asset;
  },()->{},s->{});
  Thread worker=new Thread(service::poll);worker.start();
  if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("worker did not enter");
  song.set(b);service.request(b);song.set(a);service.request(a);release.countDown();worker.join(5000);
  if(worker.isAlive()||old.get().closed!=1||service.isResolved(a)||LocalDiscServiceTest.get(service,a)!=null)throw new AssertionError("old A result escaped generation gate");
  service.poll();if(!service.isResolved(a)||LocalDiscServiceTest.get(service,a)==null)throw new AssertionError("latest A never resolves");
  service.close();System.out.println("PASS blocked A decode, UI A-B-A requests, stale A disposed exactly once, no publication before latest A resolves");
 }
}
