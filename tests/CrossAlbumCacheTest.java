package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.*;

/** File-backed LRU, aggregate pixel budget, invalidation and exact ownership. */
public class CrossAlbumCacheTest {
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static class Asset implements AutoCloseable {int closed;public void close(){closed++;}}
 static long bytes(LocalDiscService<?> service)throws Exception{var f=LocalDiscService.class.getDeclaredField("cacheBytes");f.setAccessible(true);return f.getLong(service);}
 static int size(LocalDiscService<?> service)throws Exception{var f=LocalDiscService.class.getDeclaredField("cache");f.setAccessible(true);return ((Map<?,?>)f.get(service)).size();}
 static Asset get(LocalDiscService<Asset> service,String song)throws Exception{var a=new AtomicReference<Asset>();service.drawFor(song,a::set);return a.get();}
 static void select(LocalDiscService<Asset> service,AtomicReference<String> song,String target){song.set(target);service.request(target);service.poll();}
 static String album(Path root,String name,byte[] image)throws Exception{
  Path dir=Files.createDirectory(root.resolve(name));Files.write(dir.resolve("cd.png"),image);return Files.write(dir.resolve("track.flac"),new byte[]{1}).toString();
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("cross-album-cache");byte[] png=LocalDiscServiceTest.png(0xffb42658);
  var songs=new ArrayList<String>();for(int i=0;i<6;i++)songs.add(album(root,"album"+i,png));
  var song=new AtomicReference<String>();var made=new ArrayList<Asset>();var decodes=new AtomicInteger();var log=new ArrayList<String>();
  var service=new LocalDiscService<Asset>(song::get,(b,w,h)->{decodes.incrementAndGet();var a=new Asset();made.add(a);return a;},()->{},log::add);
  var first=new ArrayList<Asset>();
  for(int i=0;i<4;i++){select(service,song,songs.get(i));first.add(get(service,song.get()));}
  check(size(service)==4&&decodes.get()==4,"four small albums share bounded cache");
  select(service,song,songs.get(0));check(get(service,song.get())==first.get(0)&&decodes.get()==4,"A-B-C-D-A exact reuse");
  select(service,song,songs.get(4));check(size(service)==4&&first.get(1).closed==1&&first.get(0).closed==0,"least recently used B evicted, touched A retained");
  // Even a cache hit cannot publish on request, before ordered file validation.
  song.set(songs.get(0));service.request(song.get());check(!service.isResolved(song.get())&&get(service,song.get())==null,"pending cache is not drawable");
  service.poll();check(get(service,song.get())==first.get(0),"revalidated cache publication");
  Path art=Path.of(song.get()).getParent().resolve("cd.png");
  Files.setLastModifiedTime(art,FileTime.fromMillis(Files.getLastModifiedTime(art).toMillis()+5000));
  int before=decodes.get();LocalDiscServiceTest.fresh(service);service.poll();
  check(first.get(0).closed==1&&decodes.get()==before+1&&get(service,song.get())!=first.get(0),"changed metadata invalidates active cached pixels");
  Asset changed=get(service,song.get());
  // Atomic replacement keeps byte size and mtime; file identity must invalidate.
  FileTime time=Files.getLastModifiedTime(art);Path replacement=art.resolveSibling("replacement.png");Files.write(replacement,png);Files.setLastModifiedTime(replacement,time);
  Object oldKey=Files.readAttributes(art,java.nio.file.attribute.BasicFileAttributes.class).fileKey();
  Files.move(replacement,art,StandardCopyOption.REPLACE_EXISTING);
  Object newKey=Files.readAttributes(art,java.nio.file.attribute.BasicFileAttributes.class).fileKey();
  LocalDiscServiceTest.fresh(service);service.poll();
  if(oldKey!=null&&!oldKey.equals(newKey))check(changed.closed==1&&get(service,song.get())!=changed,"same-size/time replacement invalidated by file identity");
  Asset current=get(service,song.get());service.discard(song.get());
  check(current.closed==1&&get(service,song.get())==null,"draw failure removes cache ownership");
  LocalDiscServiceTest.fresh(service);service.poll();check(get(service,song.get())!=current,"discarded asset never reused");
  current=get(service,song.get());Files.delete(art);LocalDiscServiceTest.fresh(service);service.poll();
  check(current.closed==1&&get(service,song.get())==null,"deleted image invalidated, no stale cache display");
  select(service,song,songs.get(2));check(get(service,song.get())!=null,"another album survives deletion/default in A");
  check(log.stream().anyMatch(s->s.startsWith("LOCAL_CD_CACHE_HIT")),"cache hits observable in diagnostics");
  song.set(null);service.request(null);check(size(service)==0&&bytes(service)==0,"no-media clears entire cache");
  service.close();service.close();for(var a:made)check(a.closed==1,"each small asset closes exactly once");

  // Test accounting with real 4000px PNG metadata; fake assets avoid a native backend.
  var large=new BufferedImage(4000,4000,BufferedImage.TYPE_BYTE_GRAY);byte[] largePng=DiscArtworkTest.bytes(large,"png");large.flush();
  var bigSongs=new ArrayList<String>();for(int i=0;i<3;i++)bigSongs.add(album(root,"big"+i,largePng));
  made.clear();decodes.set(0);song.set(null);
  var big=new LocalDiscService<Asset>(song::get,(b,w,h)->{check(w==4000&&h==4000,"real image dimensions");decodes.incrementAndGet();var a=new Asset();made.add(a);return a;},()->{},s->{});
  select(big,song,bigSongs.get(0));Asset a=get(big,song.get());select(big,song,bigSongs.get(1));Asset b=get(big,song.get());
  check(size(big)==2&&bytes(big)==32L*1024*1024,"two 2048px display copies reach 32MiB including active");
  select(big,song,bigSongs.get(0));check(get(big,song.get())==a&&decodes.get()==2,"large A-B-A saves a decode");
  select(big,song,bigSongs.get(2));check(size(big)==2&&bytes(big)<=LocalDiscService.CACHE_BYTES&&b.closed==1&&a.closed==0,"byte budget evicts LRU before entry-count limit");
  big.close();for(var asset:made)check(asset.closed==1,"large cache cleanup exactly once");
  System.out.println("PASS cross-album LRU reuse, 4-entry/32MiB aggregate bounds, pending isolation, replacement/deletion/discard invalidation, diagnostics, no-media and exact disposal");
 }
}
