package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.net.URI;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
/** Read-only, background disc artwork lookup. Publications and drawing share one lock. */
final class LocalDiscService<T extends AutoCloseable> implements AutoCloseable {
 static final List<String> NAMES=List.of("cd.png","cd.jpg","disc.png","disc.jpg");
 static final int MAX_BYTES=32*1024*1024,MAX_DIMENSION=8192;
 static final long MAX_PIXELS=16L*1024*1024;
 interface Decoder<T>{T decode(byte[] bytes,int width,int height)throws Exception;}
 interface Draw<T>{void draw(T asset)throws Exception;}
 static final long REJECT_RETRY_NANOS=TimeUnit.SECONDS.toNanos(5);
 private record Rejected(Stamp stamp,long retryAt){}
 private final Map<Path,Rejected> rejected=new LinkedHashMap<>();
 private final LongSupplier clock;
 private record Stamp(Path path,long size,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,Object key){}
 private final Supplier<String> song;
 private final Decoder<T> decoder;
 private final Runnable changed;
 private final Consumer<String> log;
 private final Object lock=new Object();
 private ScheduledExecutorService worker;
 private volatile String requestedSong;
 private String publishedSong,lastDiagnostic="";
 private Stamp publishedStamp;
 private T asset;
 // Cache owns all assets, including the current publication. No cache entry
 // becomes drawable until the latest song's ordered metadata lookup succeeds.
 static final int CACHE_ENTRIES=4;
 static final long CACHE_BYTES=32L*1024*1024;
 private record Cached<A>(A value,long bytes){}
 private final LinkedHashMap<Stamp,Cached<T>> cache=new LinkedHashMap<>(8,.75f,true);
 private long cacheBytes;
 private String resolvedSong;
 private boolean resolved;
 private volatile boolean closed;
 private long nextScan,generation;
 LocalDiscService(Supplier<String> song,Decoder<T> decoder,Runnable changed,Consumer<String> log){this(song,decoder,changed,log,System::nanoTime);}
 LocalDiscService(Supplier<String> song,Decoder<T> decoder,Runnable changed,Consumer<String> log,LongSupplier clock){this.song=song;this.decoder=decoder;this.changed=changed;this.log=log;this.clock=clock;}
 void start(){
  worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Reborn-CD-local-image");t.setDaemon(true);return t;});
  worker.scheduleWithFixedDelay(()->{try{poll();}catch(Throwable e){diagnostic("LOCAL_CD_ERROR "+failure(e));}},0,100,TimeUnit.MILLISECONDS);
 }
 static Path directory(String song){
  if(song==null||song.isBlank())return null;
  try{
   Path file;
   if(song.regionMatches(true,0,"file:",0,5))file=fileUrl(song);
   else{
    boolean drive=song.length()>2&&Character.isLetter(song.charAt(0))&&song.charAt(1)==':'&&(song.charAt(2)=='\\'||song.charAt(2)=='/');
    if(!drive&&song.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))return null;
    file=Paths.get(song);
   }
   file=file.toAbsolutePath().normalize();return Files.isRegularFile(file,NOFOLLOW_LINKS)?file.getParent():null;
  }catch(Exception e){return null;}
 }
 static Path fileUrl(String value)throws java.net.URISyntaxException{
  URI uri;
  try{uri=new URI(value);if(uri.getQuery()!=null||uri.getFragment()!=null)throw new java.net.URISyntaxException(value,"raw URL");}
  catch(java.net.URISyntaxException raw){
   String rest=value.substring(5),authority=null;
   if(rest.startsWith("//")){
    rest=rest.substring(2);int slash=rest.indexOf('/');if(slash<0)throw raw;
    authority=rest.substring(0,slash);if(authority.isEmpty())authority=null;rest=rest.substring(slash);
   }
   uri=new URI("file",authority,rest,null);
  }
  return Paths.get(uri);
 }
 private static String failure(Throwable e){
  while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();
  String reason=switch(String.valueOf(e.getMessage())){
   case "image size limit","image dimensions limit","not PNG or JPEG","unsupported PNG/JPEG","image changed while decoding" -> e.getMessage();
   default -> null;
  };
  return e.getClass().getSimpleName()+(reason==null?"":": "+reason);
 }
 // UI song observations invalidate in-flight decode, including A -> B -> A.
 void request(String current){
  if(closed||Objects.equals(current,requestedSong))return;
  synchronized(lock){
   if(closed||Objects.equals(current,requestedSong))return;
   generation++;nextScan=0;
   if(current==null||current.isBlank()){clearLocked();clearCacheLocked();rejected.clear();}
   else clearLocked();
   resolved=false;requestedSong=current;changed.run();
  }
 }
 void poll(){
  if(closed)return;
  String current=song.get();long now=clock.getAsLong(),ticket;
  request(current);
  synchronized(lock){
   if(now<nextScan)return;nextScan=now+TimeUnit.SECONDS.toNanos(1);ticket=generation;
  }
  Path directory=directory(current);boolean found=false;
  if(directory==null){
   finishDefault(current,ticket);
   diagnostic("LOCAL_CD_DEFAULT no accessible song directory");return;
  }
  if(directory!=null)for(String name:NAMES){
   if(closed||!Objects.equals(current,song.get()))return;
   Path file=directory.resolve(name);Stamp observed=null;
   try{
    if(!Files.isRegularFile(file,NOFOLLOW_LINKS)){
     synchronized(lock){evictPathLocked(file,null);}
     continue;
    }
    Stamp stamp=stamp(file);observed=stamp;
    Cached<T> cached;
    synchronized(lock){
     if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get()))return;
     evictPathLocked(file,stamp);
     Rejected failed=rejected.get(file);
     // Recheck metadata on every scan. A replacement bypasses the cooldown;
     // unchanged failures are retried later so transient decoder errors recover.
     if(failed!=null&&stamp.equals(failed.stamp())&&now-failed.retryAt()<0)continue;
     rejected.remove(file);
     if(Objects.equals(current,publishedSong)&&stamp.equals(publishedStamp)&&asset!=null)return;
     cached=cache.get(stamp);
     if(cached!=null){
      clearLocked();asset=cached.value();
      publishedSong=current;publishedStamp=stamp;resolvedSong=current;resolved=true;changed.run();
     }
    }
    if(cached!=null){diagnostic("LOCAL_CD_CACHE_HIT "+name);return;}
    if(stamp.size()>MAX_BYTES||stamp.size()<24)throw new IOException("image size limit");
    byte[] bytes;
    // Refuse a link even if the directory entry changed after the metadata check.
    try(InputStream in=Files.newInputStream(file,NOFOLLOW_LINKS)){
     bytes=in.readNBytes(MAX_BYTES+1);
    }
    int[] dimensions=dimensions(bytes);T decoded=decoder.decode(bytes,dimensions[0],dimensions[1]);
    if(decoded==null)throw new IOException("decoder returned no image");
    // Do not cache/publish a read that raced with a file replacement.
    try{if(!stamp.equals(stamp(file)))throw new IOException("image changed while decoding");}
    catch(Exception error){closeAsset(decoded);throw error;}
    synchronized(lock){
     if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get())){closeAsset(decoded);return;}
     clearLocked();
     // Keep one selected image per directory. Replacing a higher/lower priority
     // candidate retires the old selection while unrelated albums stay cached.
     for(var it=cache.entrySet().iterator();it.hasNext();){
      var entry=it.next();
      if(Objects.equals(entry.getKey().path().getParent(),directory))removeLocked(it,entry.getValue());
     }
     int side=Math.min(DiscArtwork.MAX_SIDE,Math.max(dimensions[0],dimensions[1]));
     long cost=4L*side*side;cache.put(stamp,new Cached<>(decoded,cost));cacheBytes+=cost;
     while(cache.size()>CACHE_ENTRIES||cacheBytes>CACHE_BYTES){
      var it=cache.entrySet().iterator();var entry=it.next();removeLocked(it,entry.getValue());
     }
     asset=decoded;publishedSong=current;publishedStamp=stamp;resolvedSong=current;resolved=true;changed.run();
    }
    found=true;diagnostic("LOCAL_CD_READY "+name+" "+dimensions[0]+"x"+dimensions[1]);return;
   }catch(Exception|LinkageError e){
    synchronized(lock){
     if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get()))return;
     if(observed!=null){
      rejected.put(file,new Rejected(observed,clock.getAsLong()+REJECT_RETRY_NANOS));
      while(rejected.size()>NAMES.size())rejected.remove(rejected.keySet().iterator().next());
     }
    }
    diagnostic("LOCAL_CD_REJECTED "+name+" "+failure(e));
   }
  }
  if(!found)finishDefault(current,ticket);
  diagnostic("LOCAL_CD_DEFAULT no usable cd.png/cd.jpg/disc.png/disc.jpg");
 }
 private static Stamp stamp(Path file)throws IOException{
  var attrs=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,NOFOLLOW_LINKS);
  if(!attrs.isRegularFile())throw new IOException("not a regular disc image");
  return new Stamp(file,attrs.size(),attrs.lastModifiedTime(),attrs.creationTime(),attrs.fileKey());
 }
 static int[] dimensions(byte[] bytes)throws IOException{
  byte[] signature={(byte)137,80,78,71,13,10,26,10};
  if(bytes.length>MAX_BYTES||bytes.length<24)throw new IOException("image size limit");
  boolean png=true;for(int i=0;i<signature.length;i++)if(bytes[i]!=signature[i])png=false;
  boolean jpeg=(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
  if(!png&&!jpeg)throw new IOException("not PNG or JPEG");
  try(ImageInputStream stream=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("unsupported PNG/JPEG");
   ImageReader reader=readers.next();
   try{
    reader.setInput(stream);int width=reader.getWidth(0),height=reader.getHeight(0);
    if(width<1||height<1||width>MAX_DIMENSION||height>MAX_DIMENSION||(long)width*height>MAX_PIXELS)throw new IOException("image dimensions limit");
    return new int[]{width,height};
   }finally{reader.dispose();}
  }
 }
 private void finishDefault(String current,long ticket){
  synchronized(lock){
   if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get()))return;
   boolean notify=!resolved||!Objects.equals(current,resolvedSong)||asset!=null;
   clearLocked();resolvedSong=current;resolved=true;if(notify)changed.run();
  }
 }
 // false means lookup completed without usable artwork; true means local image
 // drawn OR still pending (no disc). Resolve and draw under one publication lock.
 boolean isResolved(String currentSong){
  synchronized(lock){return !closed&&currentSong!=null&&!currentSong.isBlank()&&resolved&&Objects.equals(currentSong,resolvedSong)&&Objects.equals(currentSong,song.get());}
 }
 boolean drawResolved(String currentSong,Draw<T> draw)throws Exception{
  synchronized(lock){
   if(closed||currentSong==null||currentSong.isBlank()||!Objects.equals(currentSong,song.get()))return true;
   if(asset!=null&&Objects.equals(currentSong,publishedSong)){draw.draw(asset);return true;}
   return !resolved||!Objects.equals(currentSong,resolvedSong);
  }
 }
 boolean drawFor(String currentSong,Draw<T> draw)throws Exception{
  synchronized(lock){
   if(closed||asset==null||!Objects.equals(currentSong,publishedSong)||!Objects.equals(currentSong,song.get()))return false;
   draw.draw(asset);return true;
  }
 }
 void discard(String currentSong){
  synchronized(lock){if(Objects.equals(currentSong,publishedSong)){Path path=publishedStamp.path();evictPathLocked(path,null);changed.run();}}
  diagnostic("LOCAL_CD_DEFAULT image draw failed");
 }
 private void clearLocked(){asset=null;publishedSong=null;publishedStamp=null;}
 private void removeLocked(Iterator<?> iterator,Cached<T> entry){
  iterator.remove();cacheBytes-=entry.bytes();
  if(asset==entry.value())clearLocked();
  closeAsset(entry.value());
 }
 private void evictPathLocked(Path path,Stamp keep){
  for(var it=cache.entrySet().iterator();it.hasNext();){
   var entry=it.next();
   if(entry.getKey().path().equals(path)&&!entry.getKey().equals(keep))removeLocked(it,entry.getValue());
  }
 }
 private void clearCacheLocked(){
  for(Cached<T> entry:cache.values())closeAsset(entry.value());cache.clear();cacheBytes=0;
 }
 private void closeAsset(T value){if(value!=null)try{value.close();}catch(Exception ignored){}}
 private void diagnostic(String value){if(!value.equals(lastDiagnostic)){lastDiagnostic=value;log.accept(value);}}
 @Override public void close(){
  closed=true;if(worker!=null){worker.shutdownNow();worker=null;}
  synchronized(lock){clearLocked();clearCacheLocked();rejected.clear();changed.run();}
 }
}
