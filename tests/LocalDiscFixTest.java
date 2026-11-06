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
  check(LocalDiscService.NAMES.equals(List.of("cd.png","cd.jpg","disc.png","disc.jpg")),"exact order");
  Path folder=Files.createTempDirectory("CD 100% 中文 # ?"),song=Files.write(folder.resolve("track 01.flac"),new byte[]{1});
  String raw="file://"+song;var current=new AtomicReference<>(raw);var log=new ArrayList<String>();
  var service=new LocalDiscService<LocalDiscServiceTest.Asset>(current::get,(b,w,h)->new LocalDiscServiceTest.Asset(b),()->{},log::add);
  String[] names={"cd.png","cd.jpg","disc.png","disc.jpg"};int[] colors={0xffff0000,0xffffff00,0xff0000ff,0xff00ff00};
  for(int i=0;i<4;i++)Files.write(folder.resolve(names[i]),image(names[i].endsWith("jpg")?"jpg":"png",colors[i]));
  expect(service,raw,colors[0]); // All four coexist; CD PNG first.
  Files.write(folder.resolve("cd.png"),new byte[]{1});expect(service,raw,colors[1]);
  Files.delete(folder.resolve("cd.jpg"));expect(service,raw,colors[2]);
  Files.delete(folder.resolve("disc.png"));expect(service,raw,colors[3]);
  current.set(song.toUri().toString());expect(service,current.get(),colors[3]);
  current.set(song.toString());expect(service,current.get(),colors[3]);
  Files.write(folder.resolve("disc.jpg"),new byte[]{1});LocalDiscServiceTest.fresh(service);
  check(LocalDiscServiceTest.get(service,current.get())==null,"all invalid fallback");
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
  service.close();System.out.println("PASS PNG/JPEG four-file priority, corrupt fallback, real JPEG decode, raw host URL with spaces/percent/Unicode/#/?, encoded URL, normal path, diagnostic details");
  System.out.println("Windows SPW native rendering still requires real-machine validation.");
 }
}
