package io.github.tigerjx95.spwcdprobe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Observes a logical CUE segment independently of the physical audio path. */
final class CuePlaybackTrack {
 static final String PROPERTY="spw.cue.support.playback.segment.v1";
 private String audio,digest;
 private Long startFrame;

 static String snapshot(){
  try{return System.getProperty(PROPERTY);}
  catch(SecurityException unavailable){return null;}
 }

 boolean changed(String path,String snapshot){
  if(!Objects.equals(audio,path)){
   audio=path;
   digest=path==null?null:fingerprint(path);
   startFrame=null; // A different file is handled by the normal track gate.
  }
  Long frame=read(snapshot);
  if(frame==null)return false; // Keep the baseline through temporary CUE refreshes.
  boolean changed=startFrame!=null&&!startFrame.equals(frame);
  startFrame=frame;
  return changed;
 }

 void reset(){audio=digest=null;startFrame=null;}

 private Long read(String snapshot){
  if(digest==null||snapshot==null||snapshot.length()>128||snapshot.length()<=65
      ||snapshot.charAt(64)!=':'||!snapshot.startsWith(digest))return null;
  int end=snapshot.indexOf(':',65);
  if(end<=65)return null;
  try{
   long frame=Long.parseLong(snapshot,65,end,10);
   return frame<0?null:frame;
  }catch(NumberFormatException invalid){return null;}
 }

 private static String fingerprint(String path){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));}
  catch(NoSuchAlgorithmException unavailable){return null;}
 }
}
