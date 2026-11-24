package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.drawscope.DrawScope;

/** Flat transparent artwork, cached for the lifetime of the plugin class loader. */
final class ReelWordmark {
 private ReelWordmark(){}

 private static class Artwork {
  private static final FaceArtwork DARK=new FaceArtwork("dark");
  private static final FaceArtwork LIGHT=new FaceArtwork("light");
  private static void initialize(){}
 }

 // Use prefiltered artwork close to the display size so narrow strokes
 // survive minification even on a canvas without mipmap sampling.
 private record FaceArtwork(ImageBitmap small,ImageBitmap medium,ImageBitmap large,ImageBitmap original) {
  FaceArtwork(String name){
   this(load(name+"-64.png"),load(name+"-128.png"),load(name+"-256.png"),load(name+".png"));
  }
  ImageBitmap atWidth(float width){
   if(width<=64&&small!=null)return small;
   if(width<=128&&medium!=null)return medium;
   if(width<=256&&large!=null)return large;
   return original;
  }
 }

 private static class HubArtwork {
  private static final ImageBitmap ARC=load("hub-arc.png");
  private static void initialize(){}
 }

 private static ImageBitmap load(String name){
  return DiscImage.loadResource("/reel-wordmark/"+name);
 }

 static void prepare(boolean solid){
  // Initialize the same lazy holders touched by the visible draw.
  HubArtwork.initialize();
  if(solid)Artwork.initialize();
 }

 static boolean drawFace(DrawScope scope,float x,float y,float width,boolean lightSurface)throws Exception{
  FaceArtwork artwork=lightSurface?Artwork.DARK:Artwork.LIGHT;
  return draw(scope,artwork.atWidth(width),x,y,width);
 }

 static boolean drawHub(DrawScope scope,float radius)throws Exception{
  return draw(scope,HubArtwork.ARC,0,0,radius*.34f);
 }

 private static boolean draw(DrawScope scope,ImageBitmap bitmap,float x,float y,float width)throws Exception{
  if(bitmap==null)return false;
  DiscImage.drawBitmap(scope,bitmap,bitmap.getWidth(),bitmap.getHeight(),x,y,width);
  return true;
 }
}
