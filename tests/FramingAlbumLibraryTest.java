package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public class FramingAlbumLibraryTest {
 public static void main(String[] args)throws Exception{
  var albums=FramingAlbumLibrary.group(List.of(
      track("first","Artist","Album","1",2024),
      track("second","Artist","Album","2",2024),
      track("third","Artist","Album","3",2025),
      track("other","Guest","Album","4",2024),
      track("untagged","Guest","","5",0)));
  check(albums.size()==3,"album grouping distinguishes artist and year and skips missing album names");
  var first=albums.stream().filter(entry->entry.trackIds().contains("1")).findFirst().orElseThrow();
  check(first.trackIds().equals(List.of("1","2")),"one library row contains representative track ids");
  check(first.searchText().contains("album artist"),"album title and artist are searchable");
  var original=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB);
  original.setRGB(300,200,Color.ORANGE.getRGB());
  var bytes=DiscArtwork.encode(original);
  var preview=FramingAlbumLibrary.decode(bytes,480);
  check(preview.getWidth()==480&&preview.getHeight()==320,"cover preview preserves the original aspect");
  var thumbnail=FramingAlbumLibrary.decode(bytes,42);
  check(thumbnail.getWidth()==42&&thumbnail.getHeight()==28,"album list loads small thumbnails");
  checkPreviewPixelLimit();
  check(FramingAlbumLibrary.decode(new byte[]{1,2,3},48)==null,"invalid artwork degrades to no cover");
  check(FramingAlbumLibrary.pairedCrop(coverWithMargins(10)).equals(new VinylLabelFraming.Crop(100,0,100,0)),
      "first album uses its own 10 percent white margins");
  check(FramingAlbumLibrary.pairedCrop(coverWithMargins(5)).equals(new VinylLabelFraming.Crop(50,0,50,0)),
      "second album uses its own 5 percent white margins");
  check(FramingAlbumLibrary.pairedCrop(coverWithMargins(0))==null,"third album with no white margins is skipped");
  check(first.trackPaths().equals(List.of("/music/1","/music/2")),"album keeps representative paths for directory covers");
  checkCoverSources(first.key(),bytes);
  checkMultidiscCover();
  System.out.println("PASS album grouping, search, bounded preview decoding, failed track lookup fallback and same-directory covers");
 }

 private static void checkPreviewPixelLimit()throws Exception{
  for(int width:new int[]{4096,4097}){
   var image=new BufferedImage(width,4096,BufferedImage.TYPE_BYTE_BINARY);
   var bytes=new java.io.ByteArrayOutputStream();
   try{ImageIO.write(image,"png",bytes);}finally{image.flush();}
   var preview=FramingAlbumLibrary.decode(bytes.toByteArray(),42);
   try{
    check((preview!=null)==(width==4096),"preview must enforce the source pixel budget before subsampling");
   }finally{if(preview!=null)preview.flush();}
  }
 }

 private static void checkCoverSources(DiscSettings.AlbumKey album,byte[] embedded)throws Exception{
  var directory=Files.createTempDirectory("spw-cover-library");
  try{
   var track=Files.createFile(directory.resolve("有形的翅膀.flac"));
   var corrupt=Files.write(directory.resolve("cover.jpg"),new byte[]{1,2,3});
   var external=directory.resolve("Folder.PNG");
   var disc=directory.resolve("cd.png");
   var externalCover=new BufferedImage(90,60,BufferedImage.TYPE_INT_RGB);
   externalCover.setRGB(0,0,Color.BLUE.getRGB());
   ImageIO.write(externalCover,"png",external.toFile());
   ImageIO.write(externalCover,"png",disc.toFile());
   var path=track.toUri().toString();
   var fromDirectory=new FramingAlbumLibrary.Entry(album,List.of(),List.of(path));
   var source=new FramingAlbumLibrary.Entry(album,List.of("throws","fails","empty","works"));
   var attempts=new ArrayList<String>();
   var preferred=FramingAlbumLibrary.cover(source.withAlternatives(fromDirectory),480,id->{
    attempts.add(id);
    return switch(id){
     case "throws"->throw new IllegalStateException("invalid track");
     case "fails"->CompletableFuture.failedFuture(new IllegalStateException("stale ID"));
     case "empty"->CompletableFuture.completedFuture(null);
     default->CompletableFuture.completedFuture(embedded);
    };
   }).toCompletableFuture().join();
   check(attempts.equals(source.trackIds())&&preferred.getWidth()==480,"failed track IDs do not discard the next embedded cover");
   var thumbnail=FramingAlbumLibrary.cover(fromDirectory,42,id->{throw new AssertionError("no library ID");})
       .toCompletableFuture().join();
   check(thumbnail.getWidth()==42&&thumbnail.getHeight()==28,"same-directory folder art, case-insensitive and after broken candidate");
   var preview=FramingAlbumLibrary.cover(source.withAlternatives(fromDirectory),480,id->CompletableFuture.completedFuture(null))
       .toCompletableFuture().join();
   check(preview.getRGB(0,0)==Color.BLUE.getRGB(),"preview shares the directory fallback and preserves the cover pixels");
   Files.delete(external);
   var named=directory.resolve("有形的翅膀.PNG");
   ImageIO.write(externalCover,"png",named.toFile());
   check(FramingAlbumLibrary.cover(fromDirectory,42,id->null).toCompletableFuture().join()!=null,
       "Unicode cover with the same basename as the audio file");
   Files.delete(named);
   check(FramingAlbumLibrary.cover(fromDirectory,42,id->null).toCompletableFuture().join()==null,
       "local disc artwork and corrupt covers are not selected as album cover");
  }finally{
   try(var files=Files.newDirectoryStream(directory)){
    for(var file:files)Files.delete(file);
   }
   Files.delete(directory);
  }
 }

 private static void checkMultidiscCover()throws Exception{
  var root=Files.createTempDirectory("spw-multidisc");
  try{
   var folder=Files.createDirectory(root.resolve("THE BEST OF 2008"));
   var cover=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);
   var shared=folder.resolve("folder.jpg");
   ImageIO.write(cover,"jpg",shared.toFile());
   var album=new DiscSettings.AlbumKey("THE BEST OF 2008-2012","邓紫棋",2013,null);
   for(var name:List.of("CD1","Disc 1","Other Album")){
    var dir=Files.createDirectory(folder.resolve(name));
    var track=Files.createFile(dir.resolve("01.flac"));
    var entry=new FramingAlbumLibrary.Entry(album,List.of(),List.of(track.toString()));
    for(int size:List.of(42,480)){
     var image=FramingAlbumLibrary.cover(entry,size,id->null).toCompletableFuture().join();
     if(name.equals("Other Album"))check(image==null,"ordinary album folders do not inherit parent covers");
     else check(image!=null&&image.getWidth()==Math.min(100,size),"shared parent cover: "+name+" at "+size);
    }
    if(name.equals("Disc 1")){
     cover.setRGB(0,0,Color.BLUE.getRGB());
     ImageIO.write(cover,"png",dir.resolve("cover.png").toFile());
     check(FramingAlbumLibrary.cover(entry,480,id->null).toCompletableFuture().join().getRGB(0,0)==Color.BLUE.getRGB(),
         "disc-specific cover takes precedence over shared folder.jpg");
    }
   }
  }finally{
   try(var tree=Files.walk(root)){
    for(var path:tree.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);
   }
  }
 }

 private static BufferedImage coverWithMargins(int margin){
  var image=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);
  var graphics=image.createGraphics();
  try{
   graphics.setColor(Color.WHITE);
   graphics.fillRect(0,0,100,100);
   graphics.setColor(Color.BLUE);
   graphics.fillRect(margin,0,100-2*margin,100);
  }finally{graphics.dispose();}
  return image;
 }

 private static MediaItem track(String title,String artist,String album,String id,int year){
  return new MediaItem(title,artist,album,artist,"/music/"+id,id,year,"",0,0,false,true);
 }

 private static void check(boolean condition,String message){
  if(!condition)throw new AssertionError(message);
 }
}
