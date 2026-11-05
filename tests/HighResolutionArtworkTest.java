package io.github.tigerjx95.spwcdprobe;
import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;
public class HighResolutionArtworkTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  for(int size:new int[]{1000,3000,4000})for(boolean alpha:new boolean[]{false,true}){
   var image=new BufferedImage(size,size,alpha?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
   g.setColor(new Color(0xffba375d,true));g.fillRect(0,0,size,size);
   if(alpha){g.setComposite(AlphaComposite.Src);g.setColor(new Color(0x8065b9d7,true));g.fillRect(size/4,size/4,size/2,size/2);g.setComposite(AlphaComposite.Clear);g.fillOval(size/2-size/16,size/2-size/16,size/8,size/8);}g.dispose();
   byte[] bytes=DiscArtworkTest.bytes(image,alpha?"png":"jpg");image.flush();
   int[] dimensions=LocalDiscService.dimensions(bytes);check(dimensions[0]==size&&dimensions[1]==size,"input dimensions");
   var prepared=DiscArtwork.prepare(bytes,size,size);int side=prepared.getWidth();check(side==Math.min(size,2048),"cache bound");
   check(prepared.getRGB(0,0)==0,"transparent outside");
   if(alpha){check(prepared.getRGB(side/2,side/2)==0,"center hole");check((prepared.getRGB(side/3,side/3)>>>24)==128,"partial alpha");}
   else check((prepared.getRGB(side/2,side/2)>>>24)==255,"opaque JPEG");
   byte[] pixels=DiscArtwork.pixels(prepared);int[] raw=((DataBufferInt)prepared.getRaster().getDataBuffer()).getData();check(pixels.length==raw.length*4,"packed size");
   for(int i=0,j=0;i<raw.length;i++,j+=4){int value=(pixels[j]&255)|((pixels[j+1]&255)<<8)|((pixels[j+2]&255)<<16)|((pixels[j+3]&255)<<24);check(value==raw[i],"all premultiplied channels");}
   prepared.flush();
  }
  check(org.jetbrains.skia.Bitmap.class.getMethod("installPixels",org.jetbrains.skia.ImageInfo.class,byte[].class,int.class)!=null,"host API");
  System.out.println("PASS 1000/3000/4000 PNG+JPEG, cache bound, transparency and every BGRA pixel; native display requires SPW");
 }
}
