package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.spw.workshop.api.WorkshopApi;
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jetbrains.skia.Image;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import static io.github.tigerjx95.spwcdprobe.DiscSettings.AlbumKey;

/** Reads albums and artwork through the host's library API without invoking playback. */
final class FramingAlbumLibrary {
 private static final int TRACK_LIMIT=4;
 private static final Pattern DISC_FOLDER=Pattern.compile("(?:cd|disc|disk)[ _.-]*[0-9]+",Pattern.CASE_INSENSITIVE);
 private static final List<String> COVER_STEMS=List.of("cover","folder","front","album");
 private static final List<String> COVER_EXTENSIONS=List.of(".jpg",".jpeg",".png",".webp");

 record Entry(AlbumKey key,List<String> trackIds,List<String> trackPaths){
  Entry(AlbumKey key,List<String> trackIds){this(key,trackIds,List.of());}
  String artist(){return key.artist()==null?"":key.artist();}
  String searchText(){return (key.album()+" "+artist()+" "+(key.year()>0?key.year():"")).toLowerCase(Locale.ROOT);}
  boolean hasSources(){return !trackIds.isEmpty()||!trackPaths.isEmpty();}
  Entry withAlternatives(Entry other){
   if(other==null||!key.equals(other.key))return this;
   return new Entry(key,merge(trackIds,other.trackIds),merge(trackPaths,other.trackPaths));
  }
 }
 private record Sources(Set<String> ids,Set<String> paths){
  Sources(){this(new LinkedHashSet<>(),new LinkedHashSet<>());}
 }

 private static List<String> merge(List<String> first,List<String> second){
  var sources=new LinkedHashSet<>(first);
  second.forEach(source->addSource(sources,source));
  return List.copyOf(sources);
 }
 private static void addSource(Set<String> set,String source){
  if(set.size()<TRACK_LIMIT&&source!=null&&!source.isBlank())set.add(source);
 }

 static CompletionStage<List<Entry>> albums(){
  return WorkshopApi.library().getAllTracks().thenApplyAsync(FramingAlbumLibrary::group);
 }

 static Entry playingEntry(){
  return entry(WorkshopApi.playback().getCurrentMediaItem());
 }

 static Entry entry(MediaItem track){
  if(track==null||track.getPath()==null||track.getPath().isBlank())return null;
  var album=AlbumKey.of(track.getAlbum(),track.getAlbumArtist(),track.getArtist(),track.getYear(),track.getPath());
  var id=track.getId();
  return new Entry(album,id==null||id.isBlank()?List.of():List.of(id),List.of(track.getPath()));
 }

 static List<Entry> group(List<MediaItem> tracks){
  var albums=new HashMap<AlbumKey,Sources>();
  for(var track:tracks){
   if(track==null||track.getAlbum()==null||track.getAlbum().isBlank())continue;
   var key=AlbumKey.of(track.getAlbum(),track.getAlbumArtist(),track.getArtist(),track.getYear(),track.getPath());
   var sources=albums.computeIfAbsent(key,ignored->new Sources());
   addSource(sources.ids(),track.getId());
   addSource(sources.paths(),track.getPath());
  }
  var result=new ArrayList<Entry>(albums.size());
  albums.forEach((album,sources)->result.add(new Entry(album,List.copyOf(sources.ids()),List.copyOf(sources.paths()))));
  var comparator=java.text.Collator.getInstance(Locale.getDefault());
  result.sort(Comparator.comparing((Entry entry)->entry.key().album(),comparator)
      .thenComparing(Entry::artist,comparator).thenComparingInt(entry->entry.key().year()));
  return List.copyOf(result);
 }

 static CompletionStage<BufferedImage> cover(Entry album,int size){
  return cover(album,size,id->WorkshopApi.library().getCoverById(id));
 }
 static CompletionStage<BufferedImage> cover(Entry album,int size,Function<String,CompletionStage<byte[]>> fetch){
  return embeddedCover(album.trackIds().iterator(),size,fetch)
      .thenApplyAsync(image->image!=null?image:directoryCover(album,size));
 }
 private static CompletionStage<BufferedImage> embeddedCover(Iterator<String> ids,int size,
     Function<String,CompletionStage<byte[]>> fetch){
  while(ids.hasNext()){
   var id=ids.next();
   CompletionStage<byte[]> result;
   try{result=fetch.apply(id);}
   catch(RuntimeException|LinkageError unavailable){continue;}
   if(result==null)continue;
   return result.handleAsync((bytes,error)->error==null?decode(bytes,size):null)
       .thenCompose(image->image==null?embeddedCover(ids,size,fetch):CompletableFuture.completedFuture(image));
  }
  return CompletableFuture.completedFuture(null);
 }
 private static BufferedImage directoryCover(Entry album,int size){
  var directories=new LinkedHashMap<Path,Set<String>>();
  for(var track:album.trackPaths()){
   var audio=LocalDiscService.audioPath(track);
   if(audio==null||!Files.isRegularFile(audio,NOFOLLOW_LINKS))continue;
   var stems=directories.computeIfAbsent(audio.getParent(),ignored->new LinkedHashSet<>());
   var name=audio.getFileName().toString();
   int extension=name.lastIndexOf('.');
   if(extension>0)stems.add(name.substring(0,extension).toLowerCase(Locale.ROOT));
  }
  // Prefer artwork beside any representative track before trying a shared multidisc cover.
  var parents=new LinkedHashMap<Path,Set<String>>();
  for(var entry:directories.entrySet()){
   var dir=entry.getKey();
   if(dir.getParent()!=null&&DISC_FOLDER.matcher(dir.getFileName().toString()).matches())
    parents.computeIfAbsent(dir.getParent(),ignored->new LinkedHashSet<>()).addAll(entry.getValue());
  }
  parents.forEach(directories::putIfAbsent);
  for(var entry:directories.entrySet()){
   var stems=entry.getValue();
   if(album.key().album()!=null)stems.add(album.key().album().toLowerCase(Locale.ROOT));
   stems.addAll(COVER_STEMS);
   var coverNames=new LinkedHashSet<String>();
   for(var stem:stems)for(var extension:COVER_EXTENSIONS)coverNames.add(stem+extension);
   coverNames.removeAll(LocalDiscService.DEFAULT_NAMES);
   var candidates=new HashMap<String,Path>();
   try(var files=Files.newDirectoryStream(entry.getKey(),file->coverNames.contains(file.getFileName().toString().toLowerCase(Locale.ROOT)))){
    for(var file:files){
     if(!Files.isRegularFile(file,NOFOLLOW_LINKS))continue;
     String name=file.getFileName().toString(),key=name.toLowerCase(Locale.ROOT);
     if(name.equals(key))candidates.put(key,file);
     else candidates.putIfAbsent(key,file);
    }
   }catch(IOException|RuntimeException unavailable){continue;}
   for(var name:coverNames){
    var file=candidates.get(name);
    if(file==null)continue;
    try(var stream=Files.newInputStream(file,NOFOLLOW_LINKS)){
     var image=decode(DiscArtwork.readImageBytes(stream,Files.size(file)),size);
     if(image!=null)return image;
    }catch(IOException|RuntimeException unavailable){/* Try the next matching cover. */}
   }
  }
  return null;
 }

 static VinylLabelFraming.Crop pairedCrop(BufferedImage image){
  int width=image.getWidth(),height=image.getHeight();
  var bounds=CoverWhiteBorders.find(width,height,image::getRGB);
  return bounds.trimmedFrom(width,height)?VinylLabelFraming.Crop.fromPixels(bounds,width,height):null;
 }

 static BufferedImage decode(byte[] bytes,int maxSize){
  if(bytes==null||bytes.length==0||bytes.length>LocalDiscService.MAX_BYTES||maxSize<1)return null;
  try(var input=new DiscArtwork.ArtworkInput(bytes)){
   var readers=ImageIO.getImageReaders(input);
   if(readers.hasNext()){
    var reader=readers.next();
    try{
     reader.setInput(input,true,true);
     int width=reader.getWidth(0),height=reader.getHeight(0);
     if(!LocalDiscService.allowedDimensions(width,height))return null;
     var preview=readPreview(reader,width,height,maxSize);
     if(preview!=null)return preview;
    }finally{reader.dispose();}
   }
  }catch(IOException|RuntimeException unavailable){/* Try the host's image codec. */}
  try(var encoded=Image.Companion.makeFromEncoded(bytes)){
   if(encoded==null||!LocalDiscService.allowedDimensions(encoded.getWidth(),encoded.getHeight()))return null;
   return DiscImage.labelPreview(encoded,maxSize);
  }catch(RuntimeException|LinkageError invalid){return null;}
 }
 private static BufferedImage readPreview(javax.imageio.ImageReader reader,int width,int height,int maxSize)throws IOException{
  var parameters=reader.getDefaultReadParam();
  int step=Math.max(1,Math.max(width,height)/maxSize);
  parameters.setSourceSubsampling(step,step,0,0);
  var image=reader.read(0,parameters);
  if(image==null||Math.max(width,height)<=maxSize)return image;
  try{return scaled(image,width,height,maxSize);}finally{image.flush();}
 }
 static BufferedImage scaled(BufferedImage image,int width,int height,int maxSize){
  int edge=Math.max(width,height);
  if(edge<=maxSize)return image;
  int w=Math.max(1,(int)Math.round((double)width*maxSize/edge));
  int h=Math.max(1,(int)Math.round((double)height*maxSize/edge));
  var scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
  Graphics2D g=scaled.createGraphics();
  try{
   g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   g.drawImage(image,0,0,w,h,null);
  }finally{g.dispose();}
  return scaled;
 }

 private FramingAlbumLibrary(){}
}
