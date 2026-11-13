package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
public class LocalDiscFixTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static byte[] image(String format,int color)throws Exception{
  BufferedImage im=new BufferedImage(40,40,format.equals("jpg")?BufferedImage.TYPE_INT_RGB:BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<40;y++)for(int x=0;x<40;x++)im.setRGB(x,y,color);
  var out=new ByteArrayOutputStream();check(ImageIO.write(im,format,out),"encoder");return out.toByteArray();
 }
 static void expect(LocalDiscService<LocalDiscServiceTest.Asset> s,String song,int color)throws Exception{
  LocalDiscServiceTest.fresh(s);var asset=LocalDiscServiceTest.get(s,song);check(asset!=null,"image missing");
  int rgb=asset.png.getRGB(20,20);
  for(int shift:new int[]{0,8,16})check(Math.abs(((rgb>>shift)&255)-((color>>shift)&255))<8,"wrong candidate "+Integer.toHexString(rgb));
 }
 public static void main(String[] args)throws Exception{
  check(LocalDiscService.DEFAULT_NAMES.equals(List.of("cd.png","cd.jpg","disc.png","disc.jpg")),"exact order");
  Path folder=Files.createTempDirectory("CD 100% 中文 # ?"),song=Files.write(folder.resolve("track 01.flac"),new byte[]{1});
  String raw="file://"+song;var current=new AtomicReference<>(raw);var log=new ArrayList<String>();
  var service=new LocalDiscService<LocalDiscServiceTest.Asset>(current::get,(b,w,h)->new LocalDiscServiceTest.Asset(b),()->{},log::add);
  String[] names={"cd.png","cd.jpg","disc.png","disc.jpg"};int[] colors={0xffff0000,0xffffff00,0xff0000ff,0xff00ff00};
  for(int i=0;i<4;i++)Files.write(folder.resolve(names[i]),image(names[i].endsWith("jpg")?"jpg":"png",colors[i]));
  expect(service,raw,colors[0]); // All four coexist; CD PNG first.
  check(service.artworkStatus(raw).equals(new LocalDiscService.ArtworkStatus(true,"cd.png")),"report matched file without exposing directory");
  check(!service.artworkStatus(raw+".next").resolved(),"never report a previous track's disc");

  Files.write(folder.resolve("cd.png"),new byte[]{1});expect(service,raw,colors[1]);
  Files.delete(folder.resolve("cd.jpg"));expect(service,raw,colors[2]);
  Files.delete(folder.resolve("disc.png"));expect(service,raw,colors[3]);
  current.set(song.toUri().toString());expect(service,current.get(),colors[3]);
  current.set(song.toString());expect(service,current.get(),colors[3]);
  Files.write(folder.resolve("disc.jpg"),new byte[]{1});LocalDiscServiceTest.fresh(service);
  check(LocalDiscServiceTest.get(service,current.get())==null,"all invalid fallback");
  check(service.artworkStatus(current.get()).equals(new LocalDiscService.ArtworkStatus(true,null)),"resolved fallback has no matched file");
  check(log.stream().anyMatch(x->x.contains("LOCAL_CD_REJECTED")&&x.contains("IOException")),"diagnostic reason");
  check(log.stream().anyMatch(x->x.startsWith("LOCAL_CD_DEFAULT no usable")),"diagnostic fallback");
  check(log.stream().noneMatch(x->x.contains(folder.toString())||x.contains(song.getFileName().toString())),"diagnostic omits album and song paths");
  Path primary=folder.resolve("cd.png");
  Path fallback=folder.resolve("cd.jpg");
  Path outside=Files.createTempFile("spw-outside-album-",".png");
  Path linkedSong=folder.resolve("linked.flac");
  try{
   Files.write(primary,image("png",colors[0]));
   Files.write(fallback,image("jpg",colors[1]));
   Files.write(outside,image("png",colors[2]));
   expect(service,current.get(),colors[0]);
   var cached=LocalDiscServiceTest.get(service,current.get());
   Files.delete(primary);
   Files.createSymbolicLink(primary,outside);
   expect(service,current.get(),colors[1]);
   check(cached.closed==1,"linked replacement invalidates cached disc");
   Files.delete(fallback);
   LocalDiscServiceTest.fresh(service);
   check(service.isResolved(current.get())&&LocalDiscServiceTest.get(service,current.get())==null,
       "outside artwork link is skipped even without a fallback");
   Files.createSymbolicLink(linkedSong,song);
   check(LocalDiscService.directory(linkedSong.toString())==null,"audio file link does not open a second directory");
   System.out.println("PASS same-directory restriction, outside link rejected, cache eviction and fallback");
  }finally{
   Files.deleteIfExists(linkedSong);
   Files.deleteIfExists(primary);
   Files.deleteIfExists(fallback);
   Files.deleteIfExists(outside);
  }
  var ordered=LocalDiscService.candidateNames(" ../disc.jpg, cdart.png, 碟片.jpeg，disc.png, disc.png, cd.jpg ");
  check(ordered.equals(List.of("disc.png","cd.jpg","cd.png","disc.jpg")),"only four allowed names, without duplicates");
  Path alias=Files.write(folder.resolve("cdart.png"),image("png",colors[3]));
  service.setNames(LocalDiscService.candidateNames("cdart.png"));
  LocalDiscServiceTest.fresh(service);
  check(LocalDiscServiceTest.get(service,current.get())==null,"unrecognized names never become candidates");
  Files.write(folder.resolve("cd.jpg"),image("jpg",colors[1]));
  Path preferred=Files.write(folder.resolve("disc.png"),image("png",colors[2]));
  expect(service,current.get(),colors[1]);
  service.setNames(ordered);service.poll();
  var selected=LocalDiscServiceTest.get(service,current.get());
  check(selected!=null&&selected.png.getRGB(20,20)==colors[2],"current song rescans immediately when order changes");
  check("disc.png".equals(service.artworkStatus(current.get()).fileName()),"report switches to the actual preferred file");
  Files.write(preferred,new byte[]{1});expect(service,current.get(),colors[1]);
  check("cd.jpg".equals(service.artworkStatus(current.get()).fileName()),"report switches to the next usable file after decode failure");
  Files.delete(preferred);Files.delete(alias);
  service.close();
  verifyDirectoryReplacement();
  System.out.println("PASS PNG/JPEG four-file priority, corrupt fallback, real JPEG decode, raw host URL with spaces/percent/Unicode/#/?, encoded URL, normal path, diagnostic details");
  System.out.println("Windows SPW native rendering still requires real-machine validation.");
 }
 static void verifyDirectoryReplacement()throws Exception{
  Path root=Files.createTempDirectory("disc-parent-change");
  Path album=Files.createDirectory(root.resolve("album"));
  Path retired=root.resolve("retired");
  Path song=Files.write(album.resolve("track.flac"),new byte[]{1});
  Files.write(album.resolve("cd.png"),image("png",0xffff0000));
  var decoded=new AtomicReference<LocalDiscServiceTest.Asset>();
  try(var service=new LocalDiscService<LocalDiscServiceTest.Asset>(song::toString,(bytes,w,h)->{
   Files.move(album,retired);
   Files.createDirectory(album);
   Files.copy(retired.resolve("track.flac"),song);
   Files.write(album.resolve("cd.png"),image("png",0xff0000ff));
   var asset=new LocalDiscServiceTest.Asset(bytes);decoded.set(asset);return asset;
  },()->{},s->{})){
   service.poll();
   check(decoded.get()!=null&&decoded.get().closed==1,"directory replacement must discard the decoded image");
   check(service.isResolved(song.toString())&&LocalDiscServiceTest.get(service,song.toString())==null,
       "directory replacement must select default");
  }finally{
   for(Path directory:List.of(album,retired)){
    Files.deleteIfExists(directory.resolve("cd.png"));
    Files.deleteIfExists(directory.resolve("track.flac"));
    Files.deleteIfExists(directory);
   }
   Files.deleteIfExists(root);
  }
  System.out.println("PASS parent-directory replacement discards the old image and falls back");
 }
}
