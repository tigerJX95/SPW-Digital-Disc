package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.ImageBitmap;
import androidx.compose.ui.graphics.SkiaImageAsset_skikoKt;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import org.jetbrains.skia.Image;

/** Flat transparent artwork, cached for the lifetime of the plugin class loader. */
final class ReelWordmark {
 private ReelWordmark(){}

 private static class Artwork {
  private static final ImageBitmap DARK=load("dark.png");
  private static final ImageBitmap LIGHT=load("light.png");
 }

 private static class HubArtwork {
  private static final ImageBitmap ARC=load("hub-arc.png");
 }

 private static ImageBitmap load(String name){
  try(var stream=ReelWordmark.class.getResourceAsStream("/reel-wordmark/"+name)){
   if(stream==null)return null;
   try(Image image=Image.Companion.makeFromEncoded(stream.readAllBytes())){
    return SkiaImageAsset_skikoKt.toComposeImageBitmap(image);
   }
  }catch(Exception|LinkageError unavailable){
   return null;
  }
 }

 static boolean drawFace(DrawScope scope,float x,float y,float width,boolean lightSurface)throws Exception{
  return draw(scope,lightSurface?Artwork.DARK:Artwork.LIGHT,x,y,width);
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
