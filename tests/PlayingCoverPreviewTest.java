package io.github.tigerjx95.spwcdprobe;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayingCoverPreviewTest {
 private static FramingAlbumLibrary.Entry track(String id){
  return new FramingAlbumLibrary.Entry(new DiscSettings.AlbumKey("Shared album",null,0,null),List.of(id),List.of(id+".flac"));
 }
 public static void main(String[] args)throws Exception{
  var old=track("old");var skipped=track("skipped");var latest=track("latest");
  var started=new CountDownLatch(1);var blocked=new CompletableFuture<BufferedImage>();
  var blue=new BufferedImage(4,4,2);var calls=new AtomicInteger();
  try(var cache=new PlayingCoverPreview(entry->{
   calls.incrementAndGet();
   if(entry.equals(old)){started.countDown();return blocked;}
   if(!entry.equals(latest))throw new AssertionError("superseded read started");
   return CompletableFuture.completedFuture(blue);
  })){
   var obsolete=cache.request(old);
   if(!started.await(3,TimeUnit.SECONDS))throw new AssertionError("missing old read");
   var queued=cache.request(skipped);
   var current=cache.request(latest);
   if(current!=cache.forOpening(latest)||current.get(3,TimeUnit.SECONDS)!=blue||calls.get()!=2)
    throw new AssertionError("explicit open should bypass the older read and share the current result");
   blocked.complete(new BufferedImage(4,4,2));
   if(obsolete.join()!=null||queued.join()!=null||cache.request(latest).getNow(null)!=blue)
    throw new AssertionError("stale same-album completion leaked");
   cache.refreshIfReady(latest);
   if(cache.request(latest).get(3,TimeUnit.SECONDS)!=blue||calls.get()!=3)
    throw new AssertionError("new host publication should update the preview");
   cache.close();
   if(cache.request(old).join()!=null||calls.get()!=3)throw new AssertionError("preview survived shutdown");
  }
  System.out.println("PASS track-specific preview reuse, shared read, old network read bypass, latest-only queue, refresh and close");
 }
}
