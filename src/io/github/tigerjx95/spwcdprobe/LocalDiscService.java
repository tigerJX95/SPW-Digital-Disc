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
/** Read-only, background local PNG lookup. Publications and drawing share one lock. */
final class LocalDiscService<T extends AutoCloseable> implements AutoCloseable {
 static final List<String> NAMES=List.of("cd.png","cd.jpg","disc.png","disc.jpg");
 static final int MAX_BYTES=32*1024*1024,MAX_DIMENSION=8192;
 static final long MAX_PIXELS=16L*1024*1024;
 interface Decoder<T>{T decode(byte[] bytes,int width,int height)throws Exception;}
 interface Draw<T>{void draw(T asset)throws Exception;}
 private record Stamp(Path path,long size,long modified){}
 private final Supplier<String> song;
 private final Decoder<T> decoder;
 private final Runnable changed;
 private final Consumer<String> log;
 private final Object lock=new Object();
 private ScheduledExecutorService worker;
 private String requestedSong,publishedSong,lastDiagnostic="";
 private Stamp publishedStamp;
 private T asset;
 private String resolvedSong;
 private boolean resolved;
 private volatile boolean closed;
 private long nextScan;
 LocalDiscService(Supplier<String> song,Decoder<T> decoder,Runnable changed,Consumer<String> log){this.song=song;this.decoder=decoder;this.changed=changed;this.log=log;}
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
   file=file.toAbsolutePath().normalize();return Files.isRegularFile(file)?file.getParent():null;
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
  return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()).replace('\n',' ').replace('\r',' ');
 }
 void poll(){
  if(closed)return;
  String current=song.get();long now=System.nanoTime();
  synchronized(lock){
   if(!Objects.equals(current,requestedSong)){
    requestedSong=current;nextScan=0;clearLocked();resolved=false;changed.run();
   }
   if(now<nextScan)return;nextScan=now+TimeUnit.SECONDS.toNanos(1);
  }
  Path directory=directory(current);boolean found=false;
  if(directory==null){
   finishDefault(current);
   diagnostic("LOCAL_CD_DEFAULT no local song directory; song="+current);return;
  }
  if(directory!=null)for(String name:NAMES){
   if(closed||!Objects.equals(current,song.get()))return;
   Path file=directory.resolve(name);
   try{
    if(!Files.isRegularFile(file))continue;
    Stamp stamp=new Stamp(file,Files.size(file),Files.getLastModifiedTime(file).toMillis());
    synchronized(lock){if(Objects.equals(current,publishedSong)&&stamp.equals(publishedStamp)&&asset!=null)return;}
    byte[] bytes;
    try(InputStream in=Files.newInputStream(file)){
     bytes=in.readNBytes(MAX_BYTES+1);
    }
    int[] dimensions=dimensions(bytes);T decoded=decoder.decode(bytes,dimensions[0],dimensions[1]);
    if(decoded==null)throw new IOException("decoder returned no image");
    synchronized(lock){
     if(closed||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get())){closeAsset(decoded);return;}
     clearLocked();asset=decoded;publishedSong=current;publishedStamp=stamp;resolvedSong=current;resolved=true;changed.run();
    }
    found=true;diagnostic("LOCAL_CD_READY "+file+" "+dimensions[0]+"x"+dimensions[1]);return;
   }catch(Exception|LinkageError e){diagnostic("LOCAL_CD_REJECTED "+file+" "+failure(e));}
  }
  if(!found)finishDefault(current);
  diagnostic("LOCAL_CD_DEFAULT no usable cd.png/cd.jpg/disc.png/disc.jpg; directory="+directory);
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
 private void finishDefault(String current){
  synchronized(lock){
   if(closed||!Objects.equals(current,requestedSong)||!Objects.equals(current,song.get()))return;
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
  synchronized(lock){if(Objects.equals(currentSong,publishedSong)){clearLocked();changed.run();}}
  diagnostic("LOCAL_CD_DEFAULT image draw failed");
 }
 private void clearLocked(){T previous=asset;asset=null;publishedSong=null;publishedStamp=null;closeAsset(previous);}
 private void closeAsset(T value){if(value!=null)try{value.close();}catch(Exception ignored){}}
 private void diagnostic(String value){if(!value.equals(lastDiagnostic)){lastDiagnostic=value;log.accept(value);}}
 @Override public void close(){
  closed=true;if(worker!=null){worker.shutdownNow();worker=null;}
  synchronized(lock){clearLocked();changed.run();}
 }
}
