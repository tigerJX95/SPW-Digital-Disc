package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.channels.Channels;
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
 static final List<String> DEFAULT_NAMES=List.of("cd.png","cd.jpg","disc.png","disc.jpg");
 static List<String> candidateNames(String configured){
  if(configured==null||configured.isBlank()||configured.length()>2048)return DEFAULT_NAMES;
  var ordered=new LinkedHashSet<String>();
  for(String entry:configured.split("[,，]")){
   String name=entry.strip();
   if(DEFAULT_NAMES.contains(name))ordered.add(name);
  }
  ordered.addAll(DEFAULT_NAMES);
  return List.copyOf(ordered);
 }
 static final int MAX_BYTES=32*1024*1024,MAX_DIMENSION=8192;
 static final long MAX_PIXELS=16L*1024*1024;
 interface Decoder<T>{T decode(byte[] bytes,int width,int height)throws Exception;}
 interface Draw<T>{void draw(T asset)throws Exception;}
 static final long LOOKUP_WAIT_NANOS=TimeUnit.SECONDS.toNanos(2);
 static final long REJECT_RETRY_NANOS=TimeUnit.SECONDS.toNanos(5);
 private record Rejected(Stamp stamp,long retryAt){}
 private final Map<Path,Rejected> rejected=new LinkedHashMap<>();
 private final LongSupplier clock;
 private record Stamp(Path path,DirectoryIdentity directory,long size,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,Object key){}
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
 private long nextScan,generation,requestedAt;
 private List<String> names=DEFAULT_NAMES;
 void setNames(List<String> candidates){
  synchronized(lock){
   if(closed||names.equals(candidates))return;
   names=List.copyOf(candidates);generation++;nextScan=0;
   rejected.clear();
  }
 }
 LocalDiscService(Supplier<String> song,Decoder<T> decoder,Runnable changed,Consumer<String> log){this(song,decoder,changed,log,System::nanoTime);}
 LocalDiscService(Supplier<String> song,Decoder<T> decoder,Runnable changed,Consumer<String> log,LongSupplier clock){this.song=song;this.decoder=decoder;this.changed=changed;this.log=log;this.clock=clock;}
 void start(){
  worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Reborn-CD-local-image");t.setDaemon(true);return t;});
  worker.scheduleWithFixedDelay(()->{try{poll();}catch(Throwable e){diagnostic("LOCAL_CD_ERROR "+failure(e));}},0,100,TimeUnit.MILLISECONDS);
 }
 static Path directory(String song){
  Path audio=audioPath(song);return audio==null||!Files.isRegularFile(audio,NOFOLLOW_LINKS)?null:audio.getParent();
 }
 private static Path audioPath(String song){
  if(song==null||song.isBlank())return null;
  try{
   Path file;
   if(song.regionMatches(true,0,"file:",0,5))file=fileUrl(song);
   else{
    boolean drive=song.length()>2&&Character.isLetter(song.charAt(0))&&song.charAt(1)==':'&&(song.charAt(2)=='\\'||song.charAt(2)=='/');
    if(!drive&&song.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))return null;
    file=Paths.get(song);
   }
   return file.toAbsolutePath().normalize();
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
   resolved=false;requestedAt=clock.getAsLong();requestedSong=current;changed.run();
  }
 }
 // Called by the UI clock, independently of a potentially stalled filesystem worker.
 void resolveSlowLookup(String current){
  if(current==null||current.isBlank())return;
  synchronized(lock){
   if(closed||resolved||!Objects.equals(current,requestedSong)
     ||clock.getAsLong()-requestedAt<LOOKUP_WAIT_NANOS)return;
   resolvedSong=current;resolved=true;changed.run();
  }
 }
 void poll(){
  long observedGeneration;
  synchronized(lock){
   if(closed)return;
   observedGeneration=generation;
  }
  String current=song.get();long now=clock.getAsLong(),ticket;
  List<String> candidates;
  synchronized(lock){
   // A UI request made during the host read supersedes this poll sample.
   if(closed||observedGeneration!=generation)return;
   request(current);
   if(now<nextScan)return;nextScan=now+TimeUnit.SECONDS.toNanos(1);ticket=generation;candidates=names;
  }
  Path audio=audioPath(current);
  Path directory=audio==null?null:audio.getParent();
  if(directory==null){
   finishDefault(current,ticket);
   diagnostic("LOCAL_CD_DEFAULT no accessible song directory");return;
  }
  try(var album=AlbumDirectory.open(directory)){
   if(album.stamp(audio)==null)throw new DirectoryChanged();
   if(scan(album,current,ticket,candidates,now))return;
  }catch(IOException|SecurityException e){
   finishDefault(current,ticket);
   diagnostic("LOCAL_CD_DEFAULT album directory unavailable");return;
  }
  finishDefault(current,ticket);
  diagnostic("LOCAL_CD_DEFAULT no usable same-directory image");
 }
 private boolean scan(AlbumDirectory album,String current,long ticket,List<String> candidates,long now)throws DirectoryChanged{
  Path directory=album.path();
  for(String name:candidates){
   if(closed||!Objects.equals(current,song.get()))return true;
   Path file=directory.resolve(name);Stamp observed=null;
   try{
    Stamp stamp=album.stamp(file);
    if(stamp==null){
     synchronized(lock){
      if(closed||ticket!=generation)return true;
      evictPathLocked(file,null,true);
     }
     continue;
    }
    observed=stamp;
    Cached<T> cached;
    synchronized(lock){
     if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get()))return true;
     evictPathLocked(file,stamp,true);
     Rejected failed=rejected.get(file);
     // Recheck metadata on every scan. A replacement bypasses the cooldown;
     // unchanged failures are retried later so transient decoder errors recover.
     if(failed!=null&&stamp.equals(failed.stamp())&&now-failed.retryAt()<0)continue;
     rejected.remove(file);
     if(Objects.equals(current,publishedSong)&&stamp.equals(publishedStamp)&&asset!=null)return true;
     cached=cache.get(stamp);
     if(cached!=null){
      retirePublicationLocked();asset=cached.value();
      publishedSong=current;publishedStamp=stamp;resolvedSong=current;resolved=true;changed.run();
     }
    }
    if(cached!=null){diagnostic("LOCAL_CD_CACHE_HIT "+name);return true;}
    if(stamp.size()>MAX_BYTES||stamp.size()<24)throw new IOException("image size limit");
    byte[] bytes;
    // Refuse a link even if the directory entry changed after the metadata check.
    try(InputStream in=album.read(file)){
     if(!stamp.equals(album.stamp(file)))throw new IOException("image changed before reading");
     bytes=in.readNBytes(MAX_BYTES+1);
    }
    if(!stamp.equals(album.stamp(file)))throw new IOException("image changed while decoding");
    int[] dimensions=dimensions(bytes);T decoded=decoder.decode(bytes,dimensions[0],dimensions[1]);
    if(decoded==null)throw new IOException("decoder returned no image");
    // Do not cache/publish a read that raced with a file replacement.
    try{if(!stamp.equals(album.stamp(file)))throw new IOException("image changed while decoding");}
    catch(Exception error){closeAsset(decoded);throw error;}
    synchronized(lock){
     if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get())){closeAsset(decoded);return true;}
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
    diagnostic("LOCAL_CD_READY "+name+" "+dimensions[0]+"x"+dimensions[1]);return true;
   }catch(DirectoryChanged e){throw e;}
   catch(Exception|LinkageError e){
    synchronized(lock){
     if(closed||ticket!=generation||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get()))return true;
     if(observed!=null){
      rejected.put(file,new Rejected(observed,clock.getAsLong()+REJECT_RETRY_NANOS));
      while(rejected.size()>candidates.size())rejected.remove(rejected.keySet().iterator().next());
     }
    }
    diagnostic("LOCAL_CD_REJECTED "+name+" "+failure(e));
   }
  }
  return false;
 }
 private static final class DirectoryChanged extends IOException {}
 private record DirectoryIdentity(Path realPath,Object fileKey,java.nio.file.attribute.FileTime created){
  static DirectoryIdentity read(Path path)throws IOException{
   Path real=path.toRealPath();
   var attrs=Files.readAttributes(real,BasicFileAttributes.class,NOFOLLOW_LINKS);
   if(!attrs.isDirectory())throw new DirectoryChanged();
   return new DirectoryIdentity(real,attrs.fileKey(),attrs.creationTime());
  }
 }
 private record AlbumDirectory(Path path,DirectoryIdentity identity,DirectoryStream<Path> stream,
                              SecureDirectoryStream<Path> secure) implements AutoCloseable {
  static AlbumDirectory open(Path path)throws IOException{
   var identity=DirectoryIdentity.read(path);
   var stream=Files.newDirectoryStream(identity.realPath());
   try{
    SecureDirectoryStream<Path> secure=stream instanceof SecureDirectoryStream<Path> handle?handle:null;
    if(secure!=null){
     var attrs=secure.getFileAttributeView(BasicFileAttributeView.class).readAttributes();
     if(!attrs.isDirectory()||!Objects.equals(identity.fileKey(),attrs.fileKey())
        ||!identity.created().equals(attrs.creationTime()))throw new DirectoryChanged();
    }
    var album=new AlbumDirectory(path,identity,stream,secure);
    album.verify();return album;
   }catch(IOException|RuntimeException error){
    try{stream.close();}catch(IOException closeError){error.addSuppressed(closeError);}
    throw error;
   }
  }
  void verify()throws DirectoryChanged{
   try{if(identity.equals(DirectoryIdentity.read(path)))return;}
   catch(IOException|SecurityException ignored){}
   throw new DirectoryChanged();
  }
  Stamp stamp(Path file)throws IOException{
   verify();
   BasicFileAttributes attrs;
   Path name=file.getFileName();
   try{
    attrs=secure==null?Files.readAttributes(identity.realPath().resolve(name),BasicFileAttributes.class,NOFOLLOW_LINKS):
     secure.getFileAttributeView(name,BasicFileAttributeView.class,NOFOLLOW_LINKS).readAttributes();
   }catch(NoSuchFileException missing){verify();return null;}
   verify();
   if(!attrs.isRegularFile())return null;
   return new Stamp(file,identity,attrs.size(),attrs.lastModifiedTime(),attrs.creationTime(),attrs.fileKey());
  }
  InputStream read(Path file)throws IOException{
   verify();
   Path name=file.getFileName();
   return secure==null?Files.newInputStream(identity.realPath().resolve(name),NOFOLLOW_LINKS):
    Channels.newInputStream(secure.newByteChannel(name,Set.of(StandardOpenOption.READ,NOFOLLOW_LINKS)));
  }
  @Override public void close()throws IOException{stream.close();}
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
   retirePublicationLocked();resolvedSong=current;resolved=true;if(notify)changed.run();
  }
 }
 // false means lookup completed without usable artwork; true means local image
 // drawn OR still pending (no disc). Resolve and draw under one publication lock.
 record ArtworkStatus(boolean resolved,String fileName){}
 ArtworkStatus artworkStatus(String current){
  synchronized(lock){
   if(closed||!resolved||!Objects.equals(current,requestedSong)||!Objects.equals(current,resolvedSong))
    return new ArtworkStatus(false,null);
   return new ArtworkStatus(true,asset==null?null:publishedStamp.path().getFileName().toString());
  }
 }
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
  synchronized(lock){if(publishedStamp!=null&&Objects.equals(currentSong,publishedSong)){retirePublicationLocked();changed.run();}}
  diagnostic("LOCAL_CD_DEFAULT image draw failed");
 }
 private void clearLocked(){asset=null;publishedSong=null;publishedStamp=null;}
 private void removeLocked(Iterator<?> iterator,Cached<T> entry){
  iterator.remove();cacheBytes-=entry.bytes();
  if(asset==entry.value())clearLocked();
  closeAsset(entry.value());
 }
 private void retirePublicationLocked(){
  if(publishedStamp!=null)evictPathLocked(publishedStamp.path(),null,false);
 }
 private void evictPathLocked(Path path,Stamp keep,boolean keepPublished){
  for(var it=cache.entrySet().iterator();it.hasNext();){
   var entry=it.next();
   // Keep the current image drawable until the scan can publish its replacement or fallback.
   if(entry.getKey().path().equals(path)&&!entry.getKey().equals(keep)
     &&(!keepPublished||entry.getValue().value()!=asset))removeLocked(it,entry.getValue());
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
