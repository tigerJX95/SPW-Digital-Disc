package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

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
  check(FramingAlbumLibrary.decode(new byte[]{1,2,3},48)==null,"invalid artwork degrades to no cover");
  check(FramingAlbumLibrary.pairedCrop(coverWithMargins(10)).equals(new VinylLabelFraming.Crop(100,0,100,0)),
      "first album uses its own 10 percent white margins");
  check(FramingAlbumLibrary.pairedCrop(coverWithMargins(5)).equals(new VinylLabelFraming.Crop(50,0,50,0)),
      "second album uses its own 5 percent white margins");
  check(FramingAlbumLibrary.pairedCrop(coverWithMargins(0))==null,"third album with no white margins is skipped");
  System.out.println("PASS album grouping, search metadata, bounded preview decoding and absent artwork");
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
