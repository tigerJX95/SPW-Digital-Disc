package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.spw.workshop.api.WorkshopApi;
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.imageio.ImageIO;

import static io.github.tigerjx95.spwcdprobe.DiscSettings.AlbumKey;

/** Reads albums and artwork through the host's library API without invoking playback. */
final class FramingAlbumLibrary {
 record Entry(AlbumKey key,List<String> trackIds){
  String artist(){return key.artist()==null?"":key.artist();}
  String searchText(){return (key.album()+" "+artist()+" "+(key.year()>0?key.year():"")).toLowerCase(Locale.ROOT);}
 }

 static CompletionStage<List<Entry>> albums(){
  return WorkshopApi.library().getAllTracks().thenApplyAsync(FramingAlbumLibrary::group);
 }

 static Entry playingEntry(){
  var track=WorkshopApi.playback().getCurrentMediaItem();
  if(track==null||track.getPath()==null||track.getPath().isBlank())return null;
  var album=AlbumKey.of(track.getAlbum(),track.getAlbumArtist(),track.getArtist(),track.getYear(),track.getPath());
  var id=track.getId();
  return new Entry(album,id==null||id.isBlank()?List.of():List.of(id));
 }

 static List<Entry> group(List<MediaItem> tracks){
  var albums=new HashMap<AlbumKey,LinkedHashSet<String>>();
  for(var track:tracks){
   if(track==null||track.getAlbum()==null||track.getAlbum().isBlank())continue;
   var key=AlbumKey.of(track.getAlbum(),track.getAlbumArtist(),track.getArtist(),track.getYear(),track.getPath());
   var ids=albums.computeIfAbsent(key,ignored->new LinkedHashSet<>());
   if(ids.size()<4&&track.getId()!=null&&!track.getId().isBlank())ids.add(track.getId());
  }
  var result=new ArrayList<Entry>(albums.size());
  albums.forEach((album,ids)->result.add(new Entry(album,List.copyOf(ids))));
  var comparator=java.text.Collator.getInstance(Locale.getDefault());
  result.sort(Comparator.comparing((Entry entry)->entry.key().album(),comparator)
      .thenComparing(Entry::artist,comparator).thenComparingInt(entry->entry.key().year()));
  return List.copyOf(result);
 }

 static CompletionStage<BufferedImage> cover(Entry album,int size){
  return cover(album.trackIds().iterator(),size);
 }
 private static CompletionStage<BufferedImage> cover(Iterator<String> ids,int size){
  if(!ids.hasNext())return CompletableFuture.completedFuture(null);
  return WorkshopApi.library().getCoverById(ids.next()).thenApplyAsync(bytes->decode(bytes,size))
      .thenCompose(image->image==null?cover(ids,size):CompletableFuture.completedFuture(image));
 }

 static VinylLabelFraming.Crop pairedCrop(BufferedImage image){
  int width=image.getWidth(),height=image.getHeight();
  var bounds=CoverWhiteBorders.find(width,height,image::getRGB);
  return bounds.trimmedFrom(width,height)?VinylLabelFraming.Crop.fromPixels(bounds,width,height):null;
 }

 static BufferedImage decode(byte[] bytes,int maxSize){
  if(bytes==null||bytes.length==0)return null;
  try(var input=new DiscArtwork.ArtworkInput(bytes)){
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext())return null;
   var reader=readers.next();
   try{
    reader.setInput(input,true,true);
    int width=reader.getWidth(0),height=reader.getHeight(0);
    int edge=Math.max(width,height);
    var parameters=reader.getDefaultReadParam();
    int step=Math.max(1,edge/maxSize);
    parameters.setSourceSubsampling(step,step,0,0);
    var image=reader.read(0,parameters);
    if(image==null||edge<=maxSize)return image;
    int w=Math.max(1,(int)Math.round((double)width*maxSize/edge));
    int h=Math.max(1,(int)Math.round((double)height*maxSize/edge));
    var scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g=scaled.createGraphics();
    try{
     g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
     g.drawImage(image,0,0,w,h,null);
    }finally{g.dispose();image.flush();}
    return scaled;
   }finally{reader.dispose();}
  }catch(IOException|RuntimeException invalid){return null;}
 }

 private FramingAlbumLibrary(){}
}
