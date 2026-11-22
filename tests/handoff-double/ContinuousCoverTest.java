package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import androidx.compose.runtime.MutableState;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.LayoutCoordinates;
import com.xuncorp.spw.workshop.api.*;
import com.xuncorp.voxzen.ui.component.PlayerSheetState;
/** Runs the real production Frame against only native pixels/visibility doubles. */
public class ContinuousCoverTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.1.0-test2",".","1.19.0",Channel.MS));
  ((MutableState<Boolean>)field(CdPageProbePlugin.class,"enabled").get(plugin)).setValue(true);
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  field(type,"secondary").setBoolean(frame,true);field(type,"sheet").set(frame,new PlayerSheetState(null));
  ((MutableState<Boolean>)field(type,"ready").get(frame)).setValue(true);
  LayoutCoordinates root=(LayoutCoordinates)Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->{
   if(m.getName().equals("isAttached"))return true;throw new AssertionError(m);
  });
  field(type,"nativeRoot").set(frame,root);
  var observe=type.getDeclaredMethod("observe",String.class,DiscSettings.AlbumKey.class);observe.setAccessible(true);
  var resolve=type.getDeclaredMethod("resolveCover",Object.class);resolve.setAccessible(true);
  var drawCover=type.getDeclaredMethod("drawCover",String.class,String.class,float.class);drawCover.setAccessible(true);
  var source=type.getDeclaredMethod("source");source.setAccessible(true);
  var suppress=type.getDeclaredMethod("suppress");suppress.setAccessible(true);
  var restore=type.getDeclaredMethod("restoreNative");restore.setAccessible(true);
  var motion=(DiscTransition)field(type,"motion").get(frame);
  var originField=field(type,"coverOrigin");
  var originConstructor=originField.getType().getDeclaredConstructors()[0];originConstructor.setAccessible(true);
  var viewport=((MutableState<?>)field(type,"viewport").get(frame)).getValue();
  var origin=originConstructor.newInstance(viewport,new androidx.compose.ui.geometry.Rect(10,20,310,320));
  var sync=type.getDeclaredMethod("syncVisibility",boolean.class);sync.setAccessible(true);
  var keepPrimary=(MutableState<Boolean>)field(type,"keepPrimaryCover").get(frame);
  originField.set(frame,origin);keepPrimary.setValue(true);
  sync.invoke(frame,false);
  check(originField.get(frame)==origin&&keepPrimary.getValue(),"initial host settling must keep the captured origin");
  sync.invoke(frame,true);
  check(originField.get(frame)==origin&&keepPrimary.getValue(),"the first settled page must inherit the captured origin");
  sync.invoke(frame,false);
  check(originField.get(frame)==null&&!keepPrimary.getValue(),"leaving the settled page clears the origin");
  originField.set(frame,origin);keepPrimary.setValue(true);
  observe.invoke(frame,"another album",null);
  check(originField.get(frame)==origin,"a track change keeps the same page's layout origin");
  sync.invoke(frame,true);sync.invoke(frame,false);
  var gate=(TrackEntryGate)field(type,"trackGate").get(frame);
  var pass=(MutableState<Object>)field(type,"passPainter").get(frame);
  DiscImage pixels=new DiscImage();Object painter=new HostCoverBridge.Decoded(pixels);
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(painter,root,420*420));
  observe.invoke(frame,"album/0.flac",null);resolve.invoke(frame,painter);pass.setValue(painter);
  field(type,"suppressionApplied").setBoolean(frame,true);
  long now=1_000_000_000L;motion.enter();motion.ready(now);motion.tick(now+940_000_000L);gate.observe("album/0.flac",now);
  for(int i=1;i<=100;i++){
   String song="album/"+i+".flac";
   String outgoing="album/"+(i-1)+".flac";
   var settled=(CdPageProbePlugin.DrawCover)drawCover.invoke(frame,outgoing,outgoing,1f);
   check(settled.cover()==pixels&&settled.exposed()==1f,"matching playback keeps cover and CD");
   var racing=(CdPageProbePlugin.DrawCover)drawCover.invoke(frame,outgoing,song,1f);
   check(racing!=null&&racing.painter()==painter&&racing.cover()==pixels&&racing.exposed()==0f,
         "playback advanced after native suppression: still paint cover with no outgoing CD");
   var unavailable=(CdPageProbePlugin.DrawCover)drawCover.invoke(frame,outgoing,null,1f);
   check(unavailable.cover()==pixels&&unavailable.exposed()==0f,"unavailable playback retains cover with no CD");
   observe.invoke(frame,song,null); // First, the host still exposes the outgoing painter.
   var waiting=(CdPageProbePlugin.DrawCover)drawCover.invoke(frame,song,song,1f);
   check(waiting.cover()==pixels&&waiting.exposed()==0f,"pending cover cannot show a disc");
   check(field(type,"ownedCover").get(frame)==null,"old cover no longer qualifies as current");
   check(field(type,"pendingCover").get(frame)==pixels&&pixels.closes==0,"already displayed pixels stay alive");
   check(pass.getValue()==pixels&&(Boolean)suppress.invoke(frame),"native suppression never drops during observer-before-draw gap");
   check(resolve.invoke(frame,new Object[]{null})==pixels,"empty incoming catalog keeps same Canvas pixels");
   check(source.invoke(frame)==null&&!HostCoverBridge.imageReady(pass.getValue()),"outgoing painter and placeholder cannot prove new-track readiness");
   check(motion.pose().exposed()==0&&motion.pose().scale()==1&&motion.pose().centered()==0,"waiting cover has stationary full-size geometry and zero CD");
   check(!gate.ready(song,pass.getValue(),HostCoverBridge.imageReady(pass.getValue()),true,Long.MAX_VALUE/2),"even a long wait cannot eject a placeholder");
   DiscImage nextPixels=new DiscImage();Object next=new HostCoverBridge.Decoded(nextPixels);
   HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(painter,root,620*620),new HostCoverBridge.Image(next,root,120*120));
   check(resolve.invoke(frame,source.invoke(frame))==next,"fresh incoming source atomically replaces waiting pixels");
   check(pixels.closes==1&&field(type,"pendingCover").get(frame)==null&&field(type,"ownedCover").get(frame)==nextPixels,"old pixels released exactly once after replacement");
   pass.setValue(next);
   now=System.nanoTime()+300_000_000L;
   check(!gate.ready(song,next,true,true,now)&&gate.ready(song,next,true,true,now+120_000_000L),"fresh cover still obeys existing stable-readiness interval");
   motion.trackReady(now+120_000_000L);motion.tick(now+820_000_000L);
   pixels=nextPixels;painter=next;
   HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(painter,root,420*420));
  }
  // Real Frame missing-cover path: old owned pixels must be released, while
  // the host placeholder remains the square cover and a new real source wins.
  var availability=(CoverAvailability)field(CdPageProbePlugin.class,"coverAvailability").get(plugin);
  var presentation=type.getDeclaredMethod("resolvePresentation",Object.class);presentation.setAccessible(true);
  Object empty=androidx.compose.ui.graphics.painter.ColorPainter.class.getConstructor(long.class,kotlin.jvm.internal.DefaultConstructorMarker.class).newInstance(androidx.compose.ui.graphics.ColorKt.Color(0xff514c49),null);
  observe.invoke(frame,"missing",null);
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(empty,root,420*420));
  check(presentation.invoke(frame,new Object[]{null})==pixels,"unknown loading retains waiting cover");
  var ticket=availability.begin("missing","missing",System.nanoTime());
  availability.complete(ticket,"missing",true);
  check(presentation.invoke(frame,new Object[]{null})==empty,"confirmed absence uses host placeholder");
  check(pixels.closes==1&&field(type,"pendingCover").get(frame)==null,"missing clears old song pixels exactly once");
  check(field(type,"fallbackActive").getBoolean(frame)&&(Boolean)suppress.invoke(frame),"fallback owns Canvas");
  DiscImage recovered=new DiscImage();Object recoveredPainter=new HostCoverBridge.Decoded(recovered);
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(recoveredPainter,root,420*420));
  check(presentation.invoke(frame,recoveredPainter)==recoveredPainter&&!field(type,"fallbackActive").getBoolean(frame),"late real cover takes priority");
  pixels=recovered;
  observe.invoke(frame,"rapid/B",null);observe.invoke(frame,"rapid/C",null);
  check(field(type,"pendingCover").get(frame)==pixels&&pixels.closes==0,"rapid changes retain one bounded waiting image");
  observe.invoke(frame,(String)null,null);check(pixels.closes==1&&pass.getValue()==null,"no current media releases waiting pixels");
  DiscImage last=new DiscImage();field(type,"pendingCover").set(frame,last);
  restore.invoke(frame);check(last.closes==1&&field(type,"pendingCover").get(frame)==null&&pass.getValue()==null,"page exit releases retained image and restores native layer");
  nativeReturn(ctor.newInstance(plugin));
  System.out.println("PASS real production Frame: 100 playback-before- and playback-after-observer handoffs without native-layout gap, placeholder cannot eject, stationary cover, fresh-source atomic swap, exact disposal, rapid changes, no-media and page-exit cleanup");
  System.out.println("Native pixels and settled visibility are doubles; native Windows playback is outside this offline test.");
 }

 @SuppressWarnings("unchecked") static void nativeReturn(Object frame)throws Exception{
  var type=frame.getClass();
  var rect=new Rect(150,230,610,690);
  var centered=new Rect(570,230,1030,690);
  var bounds=new AtomicReference<>(centered);
  var outgoingBounds=new Rect(500,120,1140,760);
  var outgoing=(LayoutCoordinates)Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->{
   if(m.getName().equals("isAttached"))return true;
   throw new AssertionError(m);
  });
  var root=(LayoutCoordinates)Proxy.newProxyInstance(LayoutCoordinates.class.getClassLoader(),new Class[]{LayoutCoordinates.class},(p,m,a)->{
   if(m.getName().equals("isAttached"))return true;
   if(m.getName().equals("localBoundingBoxOf"))return a[0]==outgoing?outgoingBounds:bounds.get();
   throw new AssertionError(m);
  });
  field(type,"nativeRoot").set(frame,root);field(type,"viewportRoot").set(frame,root);
  var viewport=(MutableState<Object>)field(type,"viewport").get(frame);
  var viewportCtor=viewport.getValue().getClass().getDeclaredConstructors()[0];viewportCtor.setAccessible(true);
  viewport.setValue(viewportCtor.newInstance(1644,1024));
  var originField=field(type,"coverOrigin");
  var originCtor=originField.getType().getDeclaredConstructors()[0];originCtor.setAccessible(true);
  originField.set(frame,originCtor.newInstance(viewport.getValue(),rect));
  field(type,"coverHandoff").set(frame,CoverHandoff.from(rect,650,1));
  field(type,"suppressionApplied").setBoolean(frame,true);
  var cover=new DiscImage();field(type,"ownedCover").set(frame,cover);
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(new HostCoverBridge.Decoded(cover),root,460*460));
  var prepare=(MutableState<Boolean>)field(type,"preparingNativeReturn").get(frame);
  var retain=(MutableState<Boolean>)field(type,"retainingReturn").get(frame);
  var adjustment=(MutableState<CoverHandoff.ReturnAdjustment>)field(type,"returnAdjustment").get(frame);
  var clear=type.getDeclaredMethod("clearReturnHandoff");clear.setAccessible(true);
  var motion=(DiscTransition)field(type,"motion").get(frame);
  var close=type.getDeclaredMethod("closeMotion",long.class,Runnable.class,boolean.class);close.setAccessible(true);
  var advance=type.getDeclaredMethod("advanceMotion",long.class);advance.setAccessible(true);
  long t=1_000_000_000L;
  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);

  long start=t+1_000_000_000L,end=start+650_000_000L;
  var toggles=new AtomicInteger();
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,true);
  check(prepare.getValue()&&retain.getValue()&&toggles.get()==0,"the native small cover starts layout during retraction");
  advance.invoke(frame,start+300_000_000L);
  check(toggles.get()==0,"keep the lyrics page layout while most of the disc is visible");
  advance.invoke(frame,start+365_000_000L);
  check(toggles.get()==1&&retain.getValue()&&!motion.finished(),"request native lyrics before the cover reaches its destination");
  bounds.set(rect);
  advance.invoke(frame,start+380_000_000L);advance.invoke(frame,start+396_000_000L);
  check(retain.getValue(),"matching frames cannot release the animation before the host acknowledges navigation");
  field(type,"nativeLyrics").setBoolean(frame,true);
  bounds.set(centered);
  advance.invoke(frame,start+412_000_000L);
  check(retain.getValue(),"a centered native frame cannot replace the moving cover");
  bounds.set(rect);
  advance.invoke(frame,end-32_000_000L);
  check(retain.getValue(),"one matching frame cannot release the overlay");
  bounds.set(centered);
  advance.invoke(frame,end-16_000_000L);
  advance.invoke(frame,end);
  check(retain.getValue()&&motion.pose().scale()==DiscTransition.COVER_START_SCALE,
      "the final small cover stays visible while its native replacement is centered");
  bounds.set(new Rect(154,227,614,687));
  advance.invoke(frame,end+16_000_000L);advance.invoke(frame,end+32_000_000L);
  check(retain.getValue()&&adjustment.getValue().equals(CoverHandoff.ReturnAdjustment.ZERO),
      "a stable four-pixel drift must ease into place before handing off");
  advance.invoke(frame,end+72_000_000L);
  check(adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(2,-1.5f,0))&&retain.getValue()
       &&motion.pose().scale()==DiscTransition.COVER_START_SCALE,"mid-correction changes only position");
  advance.invoke(frame,end+112_000_000L);
  check(retain.getValue()&&adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(4,-3,0)),
      "publish the final position before showing native pixels");
  advance.invoke(frame,end+128_000_000L);
  check(!retain.getValue()&&prepare.getValue()&&toggles.get()==1,"corrected position releases one native return");
  check(adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(4,-3,0)),"retain position until the overlay is replaced");
  clear.invoke(frame);
  check(!prepare.getValue()&&adjustment.getValue().equals(CoverHandoff.ReturnAdjustment.ZERO),"native composition clears the adjustment");

  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  field(type,"nativeLyrics").setBoolean(frame,false);
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,false);
  end=start+180_000_000L;
  advance.invoke(frame,start+16_000_000L);
  check(retain.getValue()&&toggles.get()==2,"cover-only return gives the host the entire shrink duration");
  field(type,"nativeLyrics").setBoolean(frame,true);
  bounds.set(rect);
  advance.invoke(frame,end-32_000_000L);advance.invoke(frame,end-16_000_000L);
  check(retain.getValue(),"a ready native cover must still wait for the last animated pose");
  advance.invoke(frame,end);
  check(!retain.getValue()&&prepare.getValue(),"the native cover takes over on arrival without an extra hold");
  clear.invoke(frame);

  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,false);
  bounds.set(new Rect(160,240,600,680));
  advance.invoke(frame,end);
  check(retain.getValue()&&toggles.get()==3,"the cover-only fallback navigates once even after a late clock tick");
  advance.invoke(frame,end+32_000_000L);
  advance.invoke(frame,end+399_000_000L);
  check(retain.getValue()&&adjustment.getValue().equals(CoverHandoff.ReturnAdjustment.ZERO),
      "the same center at the wrong size cannot cause early release");
  advance.invoke(frame,end+400_000_000L);
  check(!retain.getValue()&&prepare.getValue(),"timeout releases an unavailable native cover");
  clear.invoke(frame);

  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,false);
  bounds.set(centered);
  advance.invoke(frame,end);
  bounds.set(new Rect(153,230,613,690));
  advance.invoke(frame,end+384_000_000L);advance.invoke(frame,end+400_000_000L);
  check(retain.getValue()&&adjustment.getValue().equals(CoverHandoff.ReturnAdjustment.ZERO),
      "a late stable target still gets the brief position correction");
  advance.invoke(frame,end+480_000_000L);advance.invoke(frame,end+496_000_000L);
  check(!retain.getValue()&&adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(3,0,0))&&toggles.get()==4,
      "late correction completes within the extended time limit");
  clear.invoke(frame);

  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  field(type,"observedSong").set(frame,"same-album/one.flac");
  var observe=type.getDeclaredMethod("observe",String.class,DiscSettings.AlbumKey.class);observe.setAccessible(true);
  var resolve=type.getDeclaredMethod("resolveCover",Object.class);resolve.setAccessible(true);
  observe.invoke(frame,"same-album/two.flac",null);
  var newCover=new HostCoverBridge.Decoded(new DiscImage());
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(newCover,root,300*460));
  resolve.invoke(frame,newCover);
  check(cover.closes==1&&originField.get(frame)!=null,"changing track releases old pixels and retains page geometry");
  motion.trackChanged();motion.trackReady(t);motion.tick(t+700_000_000L);
  end=start+650_000_000L;
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,true);
  check(prepare.getValue(),"returning after a track change prepares the native small cover");
  bounds.set(new Rect(230,230,530,690));
  advance.invoke(frame,end-32_000_000L);advance.invoke(frame,end-16_000_000L);advance.invoke(frame,end);
  check(!retain.getValue()&&motion.pose().scale()==DiscTransition.COVER_START_SCALE&&toggles.get()==5,
      "a changed cover aspect settles in the original slot and retains the animated return");
  check(Math.abs(CoverHandoff.fromSlot(bounds.get(),650,0).scale()*650-460)<.001f,
      "the return slot keeps its long side through an aspect change");
  clear.invoke(frame);
  System.out.println("PASS track change retains the native return slot across cover aspect changes");

  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,false);
  end=start+180_000_000L;
  bounds.set(new Rect(234,227,534,687));
  HostCoverBridge.nodes=List.of(
      new HostCoverBridge.Image(new HostCoverBridge.Decoded(cover),outgoing,640*640),
      new HostCoverBridge.Image(newCover,root,300*460));
  advance.invoke(frame,end);advance.invoke(frame,end+16_000_000L);
  advance.invoke(frame,end+56_000_000L);
  check(retain.getValue()&&adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(2,-1.5f,0)),
      "a larger outgoing node cannot block position correction for the returning cover");
  advance.invoke(frame,end+96_000_000L);advance.invoke(frame,end+112_000_000L);
  check(!retain.getValue()&&adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(4,-3,0))&&toggles.get()==6,
      "handoff waits for the corrected small cover even when the outgoing node remains attached");
  clear.invoke(frame);
  System.out.println("PASS simultaneous outgoing and returning native nodes");

  var clickedCover=new Rect(173,234,644,705);
  originField.set(frame,originCtor.newInstance(viewport.getValue(),clickedCover));
  bounds.set(new Rect(180,242,643,705));
  HostCoverBridge.nodes=List.of(new HostCoverBridge.Image(newCover,root,463*463));
  motion.enter();motion.ready(t);motion.tick(t+940_000_000L);
  close.invoke(frame,start,(Runnable)toggles::incrementAndGet,false);
  advance.invoke(frame,end);advance.invoke(frame,end+16_000_000L);
  advance.invoke(frame,end+56_000_000L);
  check(retain.getValue()&&adjustment.getValue().equals(new CoverHandoff.ReturnAdjustment(1.5f,2,-4)),
      "apply the position and size correction together rather than jumping after the native handoff");
  advance.invoke(frame,end+96_000_000L);
  var aligned=adjustment.getValue();
  check(retain.getValue()&&aligned.equals(new CoverHandoff.ReturnAdjustment(3,4,-8)),
      "publish the corrected rectangle before returning to native pixels");
  check(Math.abs(CoverHandoff.fromSlot(clickedCover,650,aligned.sideDelta()).scale()*650-463)<.001f,
      "the final scale uses the visible native cover's long side");
  advance.invoke(frame,end+112_000_000L);
  check(!retain.getValue()&&toggles.get()==7,"a small size difference no longer forces a timeout");
  clear.invoke(frame);
  System.out.println("PASS recorded 8 px size difference with 3 px horizontal and 4 px vertical center drift");
  System.out.println("PASS native return starts during retraction, eases measured position drift without resizing, and guards centered frames");
 }
}
