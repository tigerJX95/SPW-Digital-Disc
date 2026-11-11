package io.github.tigerjx95.spwcdprobe;
import java.util.Objects;
/** Completed host reads only; elapsed time and filenames never prove absence. */
final class CoverAvailability {
 record Ticket(String song,long generation,long request){}
 private String song;
 private long generation,request,since;
 private boolean resolved,missing,refreshRequested;
 synchronized void observe(String next,long now){
  if(Objects.equals(song,next))return;
  song=next;generation++;request=0;since=now;resolved=missing=refreshRequested=false;
 }
 synchronized Ticket begin(String path,String current,long now){
  observe(current,now);
  if(path==null||!Objects.equals(path,song))return null;
  resolved=missing=false;
  return new Ticket(song,generation,++request);
 }
 synchronized void complete(Ticket ticket,String current,boolean absent){
  if(ticket==null||ticket.generation()!=generation||ticket.request()!=request||!Objects.equals(ticket.song(),current)||!Objects.equals(song,current))return;
  resolved=true;missing=absent;
 }
 synchronized boolean available(String current){return resolved&&!missing&&Objects.equals(song,current);}
 synchronized boolean missing(String current){return resolved&&missing&&Objects.equals(song,current);}
 synchronized boolean refreshNeeded(String current,long now){
  observe(current,now);
  if(song==null||song.isBlank()||resolved||request!=0||refreshRequested||now-since<1_000_000_000L)return false;
  refreshRequested=true;return true;
 }
 synchronized void reset(){song=null;generation++;request=0;resolved=missing=refreshRequested=false;}
}
