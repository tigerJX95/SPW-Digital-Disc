package io.github.tigerjx95.spwcdprobe;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import static io.github.tigerjx95.spwcdprobe.FramingAlbumLibrary.Entry;

/** One prepared track. Superseded background reads cannot publish into the new track. */
final class PlayingCoverPreview implements AutoCloseable {
 private record Read(Entry entry,CompletableFuture<BufferedImage> image){}
 private final Function<Entry,CompletionStage<BufferedImage>> load;
 private Read current;
 private Read running,foreground;
 private boolean closed;

 PlayingCoverPreview(Function<Entry,CompletionStage<BufferedImage>> load){this.load=load;}

 synchronized CompletableFuture<BufferedImage> request(Entry entry){
  if(closed)return CompletableFuture.completedFuture(null);
  if(current!=null&&current.entry().equals(entry))return current.image();
  clear();
  if(entry==null)return CompletableFuture.completedFuture(null);
  current=new Read(entry,new CompletableFuture<>());
  dispatch();
  return current.image();
 }

 synchronized CompletableFuture<BufferedImage> forOpening(Entry entry){
  var result=request(entry);
  // A slow previous album (for example a network share) must not hold up an explicit open.
  if(current!=null&&running!=current&&foreground==null&&!result.isDone()){
   foreground=current;
   start(current);
  }
  return result;
 }

 synchronized void refreshIfReady(Entry entry){
  if(current!=null&&current.entry().equals(entry)&&current.image().isDone())clear();
 }

 private void dispatch(){
  if(running==null&&current!=null&&foreground!=current&&!current.image().isDone()){
   running=current;
   start(current);
  }
 }

 private void start(Read read){
  CompletableFuture.supplyAsync(()->load.apply(read.entry()))
      .thenCompose(stage->stage)
      .whenComplete((image,error)->finish(read,error==null?image:null));
 }

 private synchronized void finish(Read read,BufferedImage image){
  if(foreground==read)foreground=null;
  else running=null;
  if(current==read)read.image().complete(image);
  dispatch();
 }

 synchronized void clear(){
  if(current!=null){
   var previous=current;
   current=null;
   previous.image().complete(null);
  }
 }

 @Override public synchronized void close(){closed=true;clear();}
}
