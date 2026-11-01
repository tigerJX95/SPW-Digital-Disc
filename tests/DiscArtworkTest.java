package io.github.tigerjx95.spwcdprobe;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
/** Executes production normalization on PNG/JPEG pixels, independently of Skia. */
public class DiscArtworkTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static byte[] bytes(BufferedImage image,String format)throws Exception{
  var out=new ByteArrayOutputStream();check(ImageIO.write(image,format,out),"encoder");return out.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  var scan=new BufferedImage(600,300,BufferedImage.TYPE_INT_RGB);
  var g=scan.createGraphics();g.setColor(Color.RED);g.fillRect(0,0,600,300);g.setColor(Color.BLUE);g.fillRect(150,0,300,300);g.dispose();
  var cropped=DiscArtwork.prepare(bytes(scan,"jpg"),600,300);
  check(cropped.getWidth()==300&&(cropped.getRGB(150,150)&255)>245&&(cropped.getRGB(150,150)>>16&255)<8,"center square crop, no stretch");
  check(cropped.getRGB(0,0)==0&&cropped.getRGB(299,0)==0,"opaque JPG now has transparent circular corners");
  var png=new BufferedImage(400,200,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<200;y++)for(int x=0;x<400;x++)png.setRGB(x,y,0x80ff0000);
  for(int y=90;y<110;y++)for(int x=190;x<210;x++)png.setRGB(x,y,0);
  byte[] original=bytes(png,"png");var normalized=DiscArtwork.prepare(original,400,200);
  check(normalized.getWidth()==400&&normalized.getHeight()==400,"transparent rectangle fitted in square");
  check(normalized.getRGB(200,200)==0,"existing center hole preserved");
  check(normalized.getRGB(200,150)==0x80ff0000,"partial alpha and RGB preserved without backing color");
  check(normalized.getRGB(200,50)==0,"transparent fit padding");
  var roundtrip=ImageIO.read(new ByteArrayInputStream(DiscArtwork.encode(normalized)));
  check(roundtrip.getRGB(200,150)==0x80ff0000&&roundtrip.getRGB(200,200)==0,"encoded data sent to Skia retains alpha/hole");
  var huge=new BufferedImage(4096,4096,BufferedImage.TYPE_INT_RGB);
  var limited=DiscArtwork.prepare(bytes(huge,"png"),4096,4096);
  check(limited.getWidth()==2048&&limited.getHeight()==2048,"large-image cap");
  boolean softEdge=false;
  for(int x=0;x<300;x++){int alpha=cropped.getRGB(x,1)>>>24;if(alpha>0&&alpha<255)softEdge=true;}
  check(softEdge,"antialiased outer edge contains partial alpha");
  try{DiscArtwork.prepare(original,401,200);throw new AssertionError("dimension mismatch accepted");}catch(IOException expected){}
  System.out.println("PASS production artwork normalization: JPG center crop, circular antialias, transparent rectangle fit, partial alpha, center hole, PNG roundtrip, 4096->2048 cap and dimension mismatch rejection");
 }
}
