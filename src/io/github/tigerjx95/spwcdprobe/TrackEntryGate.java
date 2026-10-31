package io.github.tigerjx95.spwcdprobe;
import java.util.Objects;
/** Latest-song-only trailing debounce. Readiness cannot accumulate across songs. */
final class TrackEntryGate {
 static final long QUIET_NS=280_000_000L,READY_NS=120_000_000L;
 private boolean bound,pending,readySeen;
 private String song;
 private Object readySource;
 private long changedAt,readyAt;
 boolean observe(String current,long now){
  if(!bound){bound=true;song=current;return false;}
  if(Objects.equals(song,current))return false;
  song=current;pending=true;changedAt=now;readySeen=false;readySource=null;return true;
 }
 boolean ready(String current,Object source,boolean coverReady,boolean discReady,long now){
  if(!pending||!Objects.equals(song,current))return false;
  if(current==null||current.isBlank()||source==null||!coverReady||!discReady){readySeen=false;readySource=null;return false;}
  if(!readySeen||readySource!=source){readySource=source;readyAt=now;readySeen=true;}
  if(now-changedAt<QUIET_NS||now-readyAt<READY_NS)return false;
  pending=false;readySeen=false;readySource=null;return true;
 }
 void reset(){bound=false;pending=false;readySeen=false;song=null;readySource=null;}
}
