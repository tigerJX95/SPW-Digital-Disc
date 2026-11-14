package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
/** Real temporary files and injected monotonic time: no sleeps or native decoder. */
public class LocalDiscRetryTest {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static final long SECOND=1_000_000_000L;
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("disc-retry");
  try{
   Path track=Files.write(dir.resolve("track.flac"),new byte[]{1});String song=track.toString();
   Path primary=dir.resolve("cd.png"),fallback=dir.resolve("disc.png");
   byte[] red=LocalDiscServiceTest.png(0xffff0000),green=LocalDiscServiceTest.png(0xff00ff00);
   Files.write(primary,red);Files.write(fallback,green);
   var clock=new AtomicLong(10*SECOND);var calls=new AtomicInteger();var errors=new ArrayList<String>();
   var made=new ArrayList<LocalDiscServiceTest.Asset>();
   try(var service=new LocalDiscService<LocalDiscServiceTest.Asset>(() -> song,(bytes,w,h)->{
    calls.incrementAndGet();if(Arrays.equals(bytes,red))throw new IOException("temporarily undecodable");
    var asset=new LocalDiscServiceTest.Asset(bytes);made.add(asset);return asset;
   },()->{},errors::add,clock::get)){
    service.poll();var first=LocalDiscServiceTest.get(service,song);
    check(first!=null&&calls.get()==2,"failed primary falls through to usable lower candidate");
    for(int i=0;i<4;i++){clock.addAndGet(SECOND);service.poll();}
    check(calls.get()==2&&LocalDiscServiceTest.get(service,song)==first,"unchanged rejected primary is not decoded each second; fallback stays published");
    check(errors.stream().filter(s->s.startsWith("LOCAL_CD_REJECTED")).count()==1,"cooldown prevents repeated rejection log");
    clock.addAndGet(SECOND);service.poll();
    check(calls.get()==3&&LocalDiscServiceTest.get(service,song)==first,"failed primary retries at five seconds without replacing valid fallback");
    Files.write(primary,green);Files.setLastModifiedTime(primary,FileTime.fromMillis(Files.getLastModifiedTime(primary).toMillis()+3000));
    clock.addAndGet(SECOND);service.poll();
    check(calls.get()==4&&LocalDiscServiceTest.get(service,song)!=first&&first.closed==1,"changed primary bypasses cooldown and regains priority");
    var failedDraw=LocalDiscServiceTest.get(service,song);
    check(!service.drawResolved(song,asset->{throw new IOException("draw failed");}),
        "failed drawing permits default in the same frame");
    check(failedDraw.closed==1&&service.isResolved(song),"failed artwork retired immediately");
    clock.addAndGet(SECOND);service.poll();
    check(calls.get()==5&&"disc.png".equals(service.artworkStatus(song).fileName()),
        "failed drawing skips unchanged primary during cooldown and selects the next candidate");
    clock.addAndGet(4*SECOND);service.poll();
    check(calls.get()==6&&"cd.png".equals(service.artworkStatus(song).fileName()),
        "primary becomes eligible for retry once the draw cooldown expires");
   }
   for(var asset:made)check(asset.closed==1,"decoded assets close exactly once");
   Files.delete(fallback);clock.set(30*SECOND);calls.set(0);
   try(var service=new LocalDiscService<LocalDiscServiceTest.Asset>(() -> song,(bytes,w,h)->{
    if(calls.incrementAndGet()==1)throw new IOException("transient failure");return new LocalDiscServiceTest.Asset(bytes);
   },()->{},s->{},clock::get)){
    service.poll();check(service.isResolved(song)&&LocalDiscServiceTest.get(service,song)==null,"all failures resolve to default instead of remaining pending");
    clock.addAndGet(4*SECOND);service.poll();check(calls.get()==1,"unchanged transient failure is throttled");
    clock.addAndGet(SECOND);service.poll();check(calls.get()==2&&LocalDiscServiceTest.get(service,song)!=null,"unchanged file recovers automatically after transient decoder failure");
   }
   try(RandomAccessFile file=new RandomAccessFile(primary.toFile(),"rw")){file.setLength((long)LocalDiscService.MAX_BYTES+1);}
   Files.write(fallback,green);clock.set(50*SECOND);calls.set(0);errors.clear();
   try(var service=new LocalDiscService<LocalDiscServiceTest.Asset>(() -> song,(bytes,w,h)->{
    calls.incrementAndGet();return new LocalDiscServiceTest.Asset(bytes);
   },()->{},errors::add,clock::get)){
    service.poll();check(calls.get()==1&&LocalDiscServiceTest.get(service,song)!=null,"oversized primary is rejected and valid fallback decoded");
    check(errors.stream().anyMatch(s->s.contains("image size limit")),"oversized rejection is explained");
    Files.write(primary,red);clock.addAndGet(SECOND);service.poll();
    check(calls.get()==2&&LocalDiscServiceTest.get(service,song).png.getRGB(32,12)==0xffff0000,"replacing oversized file recovers on next normal scan");
   }
   System.out.println("PASS rejected artwork cooldown: bounded decode/draw retries, same-frame fallback, stable replacement, changed-file priority, transient recovery, oversized rejection/replacement and exact disposal");
  }finally{
   try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
  }
 }
}
