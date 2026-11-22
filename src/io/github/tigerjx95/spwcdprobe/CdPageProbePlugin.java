package io.github.tigerjx95.spwcdprobe;
import androidx.compose.runtime.*;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.draw.DrawModifierKt;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.*;
import androidx.compose.ui.platform.CompositionLocalsKt;
import androidx.compose.ui.unit.Density;
import androidx.compose.foundation.CanvasKt;
import androidx.compose.foundation.layout.*;
import com.xuncorp.spw.workshop.api.*;
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem;
import com.xuncorp.spw.workshop.api.hook.*;
import com.xuncorp.voxzen.ui.component.PlayerSheetState;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function0;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Replaces the cover DISPLAY layer with a separately measured sibling Canvas. */
public final class CdPageProbePlugin extends SpwPlugin {
 private HostBindings host;
 private final CoverAvailability coverAvailability=new CoverAvailability();
 private HostCoverStatus coverStatus;
 private final List<HookHandle> handles=new ArrayList<>();
 private final List<WeakReference<Frame>> frames=new ArrayList<>();
 private final MutableState<Boolean> enabled=state(false);
 private final ThreadLocal<Boolean> rawSource=ThreadLocal.withInitial(()->false);
 private final Path log;
 private final ThreadPoolExecutor logWriter=new ThreadPoolExecutor(0,1,2,TimeUnit.SECONDS,
   new ArrayBlockingQueue<>(256),task->{
    Thread thread=new Thread(task,"SPW Digital Disc diagnostics");
    thread.setDaemon(true);
    return thread;
   },new ThreadPoolExecutor.DiscardPolicy());
 private static final long LOG_MAX_BYTES=256L*1024;
 private static final long NATIVE_RETURN_TIMEOUT_NS=400_000_000L;
 private static final long NATIVE_RETURN_ALIGNMENT_LIMIT_NS=500_000_000L;
 private static final long ENTRY_FRAME_TIMEOUT_NS=500_000_000L;
 private volatile boolean running;
 private boolean renderWarmupPending, renderWarmupDone;
 private final EntryTiming.Series entryTimings=new EntryTiming.Series();
 private volatile GeneratedCdRaster generatedCdRaster;
 private volatile DiscSurfaceRaster<DiscSettings.ReelColor> reelRasters;
 private volatile DiscSurfaceRaster<DiscSettings.ReelColor> reelTapeRaster;
 private volatile DiscSurfaceRaster<DiscSettings.VinylColor> vinylSurfaceRaster;
 private volatile WarmupViewport warmupViewport;
 private volatile PreparedAppearance preparedAppearance;
 private javax.swing.Timer sourceObserver,animationClock;
 private final MutableState<Long> sourceRevision=state(0L);
 private final DiscSettings.ReelRandom reelRandom=new DiscSettings.ReelRandom();
 private final int vinylColorSalt=java.util.concurrent.ThreadLocalRandom.current().nextInt();
 private final MutableState<Long> discRevision=state(0L);
 private LocalDiscService<DiscImage> localDiscs;
 private final MutableState<DiscSettings.Values> displaySettings=state(DiscSettings.DEFAULT);
 private DiscSettings discSettings;
 private final PlaybackStateReader playbackState=new PlaybackStateReader();
 private record Viewport(int width,int height){}
 private record WarmupViewport(Viewport size,float density){
  RenderWarmup.Target target(int amount){
   var plan=SelfDrawLayout.fit(size.width(),size.height(),density,true,amount);
   return RenderWarmup.Target.fromLayout(plan,density,amount);
  }
 }
 private record PreparedAppearance(DiscSettings.Appearance appearance,RootRenderer.Finishes finishes){}
 private record CoverOrigin(Viewport viewport,Rect bounds){}
 record DrawCover(Object painter, DiscImage cover, float exposed) {}
 private final class Frame {
  final MutableState<Viewport> viewport=state(new Viewport(0,0));
  final MutableState<Object> artwork=state(null);
  final MutableState<Object> rejectedSource=state(null);
  final MutableState<Boolean> ready=state(false);
  volatile LayoutCoordinates nativeRoot;
  LayoutCoordinates viewportRoot;
  LayoutCoordinates widgetRoot;
  float widgetWidth,widgetHeight;
  PlayerSheetState sheet;
  boolean renderAllowed=true,failed,suppressionApplied,secondary,nativeLyrics;
  Object lastPainted;
  boolean revealMeasured, slowEntryDrawLogged;
  boolean spinMeasured;
  long previousSpinDraw;
  EntryTiming entryTiming;
  final MutableState<Boolean> entryFramesRequested=state(false);
  Object entryClockOwner;
  long lastEntryFrame;
  boolean entryClockTimedOut;
  boolean openDiscDrawn;
  CoverHandoff coverHandoff;
  CoverOrigin coverOrigin;
  final MutableState<Boolean> keepPrimaryCover=state(false);
  DiscSettings.ReelColor reelRasterChoice;
  GeneratedCdRaster.Key cdRasterChoice;
  boolean useCdRaster,cdRasterDrawn;
  boolean tapeRasterDrawn;
  DiscSettings.VinylColor vinylRasterChoice;
  boolean useVinylRaster,vinylRasterDrawn;
  boolean useReelRaster, rasterDrawn;
  final MutableState<Object> passPainter=state(null);
  Object observedSource;
  boolean observedReady,observedMissing,fallbackActive;
  String observedSong;
  final MutableState<DiscSettings.AlbumKey> observedAlbum=state(null);
  ReflectionRenderer reflection;
  final RootRenderer.CoverCorners coverCorners=new RootRenderer.CoverCorners();
  final FrontDiscLayer frontDiscLayer=new FrontDiscLayer();
  final DiscRotation rotation=new DiscRotation();
  final MutableState<Float> rotationAngle=state(0f);
  final MutableState<Boolean> retainingReturn=state(false);
  final MutableState<Boolean> preparingNativeReturn=state(false);
  final CoverHandoff.ReturnPosition returnPosition=new CoverHandoff.ReturnPosition();
  final MutableState<CoverHandoff.ReturnAdjustment> returnAdjustment=state(CoverHandoff.ReturnAdjustment.ZERO);
  Runnable nativeReturnAction;
  long nativeReturnStarted;
  final RevealCycle revealCycle=new RevealCycle();
  final CoverSelection coverSelection=new CoverSelection();
  final TrackEntryGate trackGate=new TrackEntryGate();
  final CuePlaybackTrack cueTrack=new CuePlaybackTrack();
  final MutableState<Boolean> trackPending=state(false);
  final DiscTransition motion=new DiscTransition();
  final MutableState<DiscTransition.Pose> pose=state(motion.pose());
  void freezeRotation(long now){rotationAngle.setValue(rotation.freeze(now));}
  void closeMotion(long now,Runnable done,boolean visibleDisc){
   if(motion.closing()||motion.finished())return;
   freezeRotation(now);
   motion.setSpeed(displaySettings.getValue().motionSpeed());
   boolean originReady=coverOrigin!=null&&coverHandoff!=null&&suppressionApplied
       &&coverOrigin.viewport().equals(viewport.getValue());
   clearEntryTiming();
   motion.close(now,()->{
    pose.setValue(motion.pose());
    if(!preparingNativeReturn.getValue()){retainingReturn.setValue(false);done.run();}
   },visibleDisc,originReady);
   // Keep the Canvas visible while the native layout and its shadow settle.
   if(originReady&&motion.closing()&&visualCover()!=null){
    nativeReturnAction=done;
    preparingNativeReturn.setValue(true);
    retainingReturn.setValue(true);
   }
   pose.setValue(motion.pose());
  }
  void advanceMotion(long now){
   boolean redrawEntry=motion.pageEntryAnimating();
   boolean redrawMotion=redrawEntry||motion.closing();
   if(motion.phase()!=DiscTransition.Phase.OPEN)openDiscDrawn=false;
   boolean changed=motion.tick(now);
   // Widget width conservatively bounds both cover centering and disc travel,
   // including full exposure. Never advance navigation during the brake.
   changed=motion.finishSubpixelReturn(now,widgetWidth)||changed;
   if(changed){
    pose.setValue(motion.pose());
    // Request a draw even when Compose batches state notifications.
    if(redrawMotion&&suppressionApplied&&CanvasWidget.requestDraw(widgetRoot)&&redrawEntry&&entryTiming!=null)
     entryTiming.drawRequested();
   }
   if(preparingNativeReturn.getValue()&&retainingReturn.getValue())observeNativeReturn(now);
  }
  boolean canUseEntryClock(){
   return running&&!entryClockTimedOut&&motion.pageEntryAnimating()&&suppressionApplied&&ready.getValue()
       &&widgetRoot!=null&&widgetRoot.isAttached();
  }
  void advanceFromTimer(long now){
   if(entryClockOwner!=null&&motion.pageEntryAnimating()&&now-lastEntryFrame>=ENTRY_FRAME_TIMEOUT_NS){
    entryClockTimedOut=true;entryClockOwner=null;
    report("ENTRY_FRAME_CLOCK_FALLBACK timedOut=true");
   }
   boolean useEntryClock=canUseEntryClock();
   entryFramesRequested.setValue(useEntryClock);
   // Before Compose delivers its first frame, or on hosts without a frame clock,
   // the timer keeps the original source-ready start and motion behavior.
   if(!useEntryClock||entryClockOwner==null)advanceMotion(now);
  }
  Object driveEntryFrames(Continuation<? super Unit> completion){
   Object owner=new Object();
   return ComposeEntryFrames.run(completion,()->{
    if(!javax.swing.SwingUtilities.isEventDispatchThread()||!canUseEntryClock())return false;
    entryClockOwner=owner;
    long now=System.nanoTime();
    lastEntryFrame=now;
    if(entryTiming!=null)entryTiming.hostFrame(now);
    advanceMotion(now);
    return canUseEntryClock();
   },()->{if(entryClockOwner==owner)entryClockOwner=null;},
       error->report("ENTRY_FRAME_CLOCK_FALLBACK "+error.getClass().getSimpleName()));
  }
  void observeNativeReturn(long now){
   // Start the host transition as the exposed disc becomes a sliver.
   // The host can then draw its shadow in the first native small-cover frame.
   if(nativeReturnAction!=null&&motion.pose().exposed()<=.15f){
    Runnable action=nativeReturnAction;
    nativeReturnAction=null;
    action.run();
   }
   Rect origin=coverOrigin!=null&&coverOrigin.viewport().equals(viewport.getValue())?coverOrigin.bounds():null;
   Rect nativeBounds=nativeLyrics&&origin!=null?nativeCoverBounds(origin):null;
   var priorAdjustment=returnAdjustment.getValue();
   var adjustment=returnPosition.observe(origin,nativeBounds,now);
   returnAdjustment.setValue(adjustment);
   if(!motion.finished())return;
   if(nativeReturnStarted==0)nativeReturnStarted=now;
   // Give the corrected position a frame before releasing the overlay.
   boolean aligned=returnPosition.aligned(priorAdjustment)&&returnPosition.aligned(adjustment);
   long hold=now-nativeReturnStarted;
   long limit=returnPosition.stable()?NATIVE_RETURN_ALIGNMENT_LIMIT_NS:NATIVE_RETURN_TIMEOUT_NS;
   if(aligned||hold>=limit){
    report("COVER_RETURN_HANDOFF aligned="+aligned+" holdMs="+hold/1_000_000L
        +" dx="+adjustment.x()+" dy="+adjustment.y()+" sideDelta="+adjustment.sideDelta());
    // Keep the final transform and reflection until the native composition
    // actually replaces the Canvas.
    if(nativeLyrics)retainingReturn.setValue(false);
    else clearReturnHandoff();
   }
  }
  void clearReturnHandoff(){
   nativeReturnAction=null;nativeReturnStarted=0;
   returnPosition.reset();returnAdjustment.setValue(CoverHandoff.ReturnAdjustment.ZERO);
   preparingNativeReturn.setValue(false);retainingReturn.setValue(false);
  }
  String lastBoundsError="";
  DiscImage ownedCover,pendingCover;
  Object ownedSource,lastCaptureFailure;
  String handoffDiagnostic="";
  void releaseCover(DiscImage cover){
   if(cover==null)return;
   var cache=generatedCdRaster;
   if(cache!=null)cache.release(cover);
   cover.close();
  }
  void clearCover(){
   fallbackActive=false;
   releaseCover(ownedCover);ownedCover=null;
   releaseCover(pendingCover);pendingCover=null;
   cdRasterChoice=null;
   ownedSource=null;lastCaptureFailure=null;
  }
  void awaitCover(){
   // Retain only the pixels already on screen, in the same Canvas geometry.
   // This is a visual placeholder, never proof of current-track readiness.
   if(ownedCover!=null){
    releaseCover(pendingCover);
    pendingCover=ownedCover;ownedCover=null;
   }
   ownedSource=null;lastCaptureFailure=null;
  }
  DiscImage visualCover(){return ownedCover!=null?ownedCover:pendingCover;}
  DrawCover drawCover(String drawSong,String playbackSong,float exposed){
   Object painter=passPainter.getValue();
   if(!secondary||painter==null)return null;
   // The native layer already ceded this pass. Retain its selected cover
   // even if playback advanced, but never expose a CD for the outgoing song.
   boolean showDisc=drawSong!=null&&Objects.equals(drawSong,playbackSong)&&(ownedCover!=null||pendingCover==null);
   return new DrawCover(painter,visualCover(),showDisc?exposed:0f);
  }
  void prepareHiddenDisc(androidx.compose.ui.graphics.drawscope.DrawScope scope, float exposed, int amount,
      float aspect, boolean trimmed, DiscSettings.Style style, DiscSettings.VinylColor vinylColor,
      DiscSettings.ReelColor reelColor, java.util.function.Supplier<androidx.compose.ui.graphics.Path> paths,
      java.util.function.Supplier<LocalDiscService.ArtworkStatus> artworkStatus){
   if(exposed!=0||amount<=0||motion.closing()||motion.finished())return;
   var status=artworkStatus.get();
   if(!status.resolved()||status.fileName()!=null)return;
   var tape=reelTapeRaster;
   if(style.reel()&&tape!=null)tape.prepare(DiscSettings.ReelColor.DEFAULT);
   var vinyl=vinylSurfaceRaster;
   if(style.vinyl()&&vinyl!=null)vinyl.prepare(vinylColor);
   if(style==DiscSettings.Style.REEL&&reelRasters!=null)reelRasters.prepare(reelColor);
   // A lookup can finish after the first cover draw. Every hidden pass
   // can prepare the current style and size; the renderer reuses hot caches.
   try{RootRenderer.prepareDefaultDisc(scope,amount,aspect,trimmed,style,vinylColor,reelColor,paths);}
   catch(Exception|LinkageError error){report("DISC_PREPARE_ERROR "+error.getClass().getSimpleName());}
  }
  boolean drawPreparedCd(GeneratedCdRaster cache,GeneratedCdRaster.Key key,
      androidx.compose.ui.graphics.drawscope.DrawScope target,float cx,float cy,float diameter){
   if(cache==null||key==null)return false;
   if(!key.equals(cdRasterChoice)){
    cdRasterChoice=key;
    // Choose once before any movement. A raster that finishes late must not
    // cause the renderer to switch mid-eject or on the first rotation frame.
    useCdRaster=cache.ready(key);
   }
   if(!useCdRaster)return false;
   useCdRaster=cache.draw(key,target,cx,cy,diameter);
   cdRasterDrawn|=useCdRaster;
   return useCdRaster;
  }
  boolean drawPreparedVinyl(DiscSurfaceRaster<DiscSettings.VinylColor> cache,DiscSettings.VinylColor color,
      androidx.compose.ui.graphics.drawscope.DrawScope target,float radius){
   if(cache==null)return false;
   if(vinylRasterChoice!=color){
    vinylRasterChoice=color;
    // Use the same surface path when the label first starts to rotate.
    useVinylRaster=cache.ready(color,radius*2);
   }
   if(!useVinylRaster)return false;
   useVinylRaster=cache.draw(color,target,0,0,radius*2);
   vinylRasterDrawn|=useVinylRaster;
   return useVinylRaster;
  }
  boolean drawPreparedReel(DiscSurfaceRaster<DiscSettings.ReelColor> cache,DiscSettings.ReelColor color,float angle,
      androidx.compose.ui.graphics.drawscope.DrawScope target,float cx,float cy,float diameter){
   if(cache==null)return false;
   if(reelRasterChoice!=color){
    reelRasterChoice=color;
    useReelRaster=cache.ready(color,diameter);
   }
   if(!useReelRaster)return false;
   useReelRaster=cache.draw(color,target,cx,cy,diameter,angle);
   rasterDrawn|=useReelRaster;
   return useReelRaster;
  }
  Object paintSource(){
   if(!presentationVisible())return null;
   MediaItem item;
   try{item=WorkshopApi.playback().getCurrentMediaItem();}catch(Exception unavailable){return null;}
   String song=item==null?null:item.getPath();
   if(!Objects.equals(song,observedSong)){
    observe(song,albumKey(item));
    return resolvePresentation(observedSource);
   }
   return resolvePresentation(source());
  }
  Object resolvePresentation(Object live){
   if(live==null&&coverAvailability.missing(observedSong)){
    Object placeholder=placeholder();
    if(placeholder!=null){
     if(visualCover()!=null)clearCover();
     fallbackActive=true;return placeholder;
    }
   }
   fallbackActive=false;
   return resolveCover(live);
  }
  Object resolveCover(Object live){
   if(live!=null&&live!=ownedSource&&live!=lastCaptureFailure)try{
    long started=System.nanoTime();
    DiscImage copy=HostCoverBridge.capture(live);
    DiscImage previous=ownedCover;ownedCover=copy;ownedSource=live;
    releaseCover(previous);
    releaseCover(pendingCover);pendingCover=null;
    report("COVER_OWNED source="+System.identityHashCode(live)+" elapsedUs="+elapsedMicros(started));
   }catch(Exception|LinkageError error){lastCaptureFailure=live;report("COVER_CAPTURE_ERROR "+error.getClass().getSimpleName());}
   // The host may dispose every outgoing image node when its own transition
   // finishes. Our already displayed pixels belong to this song, not that node.
   // DiscImage is deliberately NOT a host painter: imageReady() stays false
   // for the retained placeholder until capture of a fresh source succeeds.
   return ownedCover!=null?ownedSource:pendingCover!=null?pendingCover:live;
  }
  boolean presentationVisible(){
   return secondary&&renderAllowed&&Boolean.TRUE.equals(enabled.getValue())&&sheet!=null&&SheetStateBinding.current().visible(sheet);
  }
  void refreshReady(){
   // A reused Canvas need not receive another position callback after collapse,
   // while content() clears ready during the hidden interval. Re-read the live
   // coordinates so readiness cannot remain false for the entire presentation.
   boolean fits=false;
   if(!failed&&widgetRoot!=null&&widgetRoot.isAttached())try{
    long measured=size(widgetRoot);
    fits=Math.abs((int)(measured>>>32)-widgetWidth)<=3&&Math.abs((int)measured-widgetHeight)<=3;
   }catch(Exception ignored){}
   ready.setValue(fits);
  }
  boolean suppress(){
   discRevision.getValue(); // Resolve completion must redraw even while Canvas is waiting.
   sourceRevision.getValue(); // Native subtree/cached image completion is not always a parent recomposition.
   return presentationVisible()&&Boolean.TRUE.equals(ready.getValue())&&!failed&&(fallbackActive||visualCover()!=null||HostCoverBridge.imageReady(source()))&&(pendingCover!=null||Boolean.TRUE.equals(trackPending.getValue())||discResolved());
  }
  void clearEntryTiming(){
   entryTiming=null;
  }
  void restoreNative(){
   entryFramesRequested.setValue(false);entryClockOwner=null;
   entryClockTimedOut=openDiscDrawn=false;
   clearCover();lastPainted=null;rotation.reset();rotationAngle.setValue(0f);
   clearReturnHandoff();
   clearEntryTiming();coverHandoff=null;coverOrigin=null;keepPrimaryCover.setValue(false);
   secondary=false;ready.setValue(false);widgetRoot=null;revealCycle.reset();motion.reset();trackGate.reset();cueTrack.reset();trackPending.setValue(false);pose.setValue(motion.pose());
   if(suppressionApplied)HostCoverBridge.invalidate(nativeRoot);
   passPainter.setValue(null);suppressionApplied=false;
  }
  void syncVisibility(boolean visible){
   boolean wasVisible=revealCycle.visible();
   RevealCycle.Edge edge=revealCycle.sample(visible);
   if(edge==RevealCycle.Edge.NONE)return;
   entryFramesRequested.setValue(false);entryClockOwner=null;
   entryClockTimedOut=openDiscDrawn=false;
   if(edge==RevealCycle.Edge.HIDDEN){
    freezeRotation(System.nanoTime());clearCover();
    // Keep the click's origin while the host is still settling the initial page.
    if(wasVisible){coverOrigin=null;keepPrimaryCover.setValue(false);}
   }
   // No old pass, pending return or track request may leak into the next presentation.
   motion.reset();trackGate.reset();cueTrack.reset();trackPending.setValue(false);clearReturnHandoff();
   passPainter.setValue(null);suppressionApplied=false;lastPainted=null;
   clearEntryTiming();coverHandoff=null;
   if(edge==RevealCycle.Edge.SHOWN){
    revealMeasured=slowEntryDrawLogged=spinMeasured=false;previousSpinDraw=0;motion.enter();
    entryTiming=entryTimings.begin(System.nanoTime());
    report("PRESENTATION_READY restart cover-first entry");
   }
   pose.setValue(motion.pose());HostCoverBridge.invalidate(nativeRoot);
  }
  List<HostCoverBridge.Image> images(){
   var root=nativeRoot;return root!=null&&root.isAttached()?HostCoverBridge.images(root):List.of();
  }
  CoverOrigin captureCoverOrigin(){
   String song;try{song=currentSong();}catch(RuntimeException unavailable){return null;}
   if(song==null||song.isBlank())return null;
   Viewport measured=viewport.getValue();
   Rect cover=nativeCoverBounds(null);
   return cover==null?null:new CoverOrigin(measured,cover);
  }
  Rect nativeCoverBounds(Rect returnOrigin){
   var root=viewportRoot;
   if(root==null||!root.isAttached())return null;
   Viewport measured=viewport.getValue();
   Rect cover=null;
   long area=0;
   for(var image:images()){
    if(!HostCoverBridge.imageReady(image.painter())||!image.coordinates().isAttached())continue;
    if(image.area()<area)break;
    try{
     Rect bounds=root.localBoundingBoxOf(image.coordinates(),false);
     if(CoverHandoff.visibleOrigin(bounds,measured.width(),measured.height())
         &&(returnOrigin==null||CoverHandoff.matchesReturnSlot(returnOrigin,bounds))
         &&(cover==null||image.area()>area||bounds.getTop()<cover.getTop())){
      // At equal sizes the lower node can be the cover reflection.
      cover=bounds;area=image.area();
     }
    }catch(RuntimeException ignored){ /* The outgoing node may already be detached. */ }
   }
   return cover;
  }
  CoverHandoff measureCoverHandoff(Object painter,float side,float aspect){
   if(widgetRoot==null||!widgetRoot.isAttached())return CoverHandoff.FALLBACK;
   for(var image:images()){
    if(image.painter()!=painter||!image.coordinates().isAttached())continue;
    try{
     var bounds=widgetRoot.localBoundingBoxOf(image.coordinates(),false);
     var handoff=CoverHandoff.from(bounds,side,aspect);
     if(handoff!=CoverHandoff.FALLBACK){
      report("COVER_HANDOFF nativePx="+bounds.getWidth()+"x"+bounds.getHeight()
          +" overlaySidePx="+side+" startScale="+handoff.scale()+" centered=true");
      return handoff;
     }
    }catch(RuntimeException ignored){ /* A native layout node can detach during handoff. */ }
   }
   report("COVER_HANDOFF native geometry unavailable");
   return CoverHandoff.FALLBACK;
  }
  boolean owns(Object painter,long drawSize){
   var root=nativeRoot;
   if(root==null||!root.isAttached())return false;
   float w=RootRenderer.x(drawSize),h=RootRenderer.y(drawSize);
   return HostCoverBridge.anyPainter(root,painter,coordinates->{
    try{
     long measured=size(coordinates);
     return Math.abs((int)(measured>>>32)-w)<=Math.max(6,w*.05f)
         &&Math.abs((int)measured-h)<=Math.max(6,h*.05f);
    }catch(Exception ignored){return false;}
   });
  }
  Object placeholder(){
   for(var image:images()){
    Object painter=image.painter();
    String type=painter==null?"":painter.getClass().getName();
    boolean placeholder=type.equals("androidx.compose.ui.graphics.painter.BitmapPainter")
      ||type.equals("androidx.compose.ui.graphics.painter.ColorPainter")||type.equals("androidx.compose.ui.graphics.vector.VectorPainter");
    if(placeholder&&painter!=rejectedSource.getValue())return painter;
   }
   return null;
  }
  Object source(){
   Object captured=artwork.getValue(),rejected=rejectedSource.getValue();
   var current=images();
   List<Object> decoded=new ArrayList<>();
   for(var image:current)if(image.painter()!=rejected&&HostCoverBridge.imageReady(image.painter()))decoded.add(image.painter());
   return coverSelection.select(decoded,captured,System.nanoTime());
  }

  boolean observe(String song,DiscSettings.AlbumKey album){
   coverAvailability.observe(song,System.nanoTime());
   coverSelection.song(song);
   boolean changed=!Objects.equals(song,observedSong);
   boolean albumChanged=!Objects.equals(album,observedAlbum.getValue());
   if(albumChanged)observedAlbum.setValue(album);
   if(changed){
    fallbackActive=false;rotation.reset();rotationAngle.setValue(0f);
    boolean previouslyObserved=observedSong!=null;
    if(song!=null&&!song.isBlank())awaitCover();else clearCover();
    observedSong=song;failed=false;rejectedSource.setValue(null);artwork.setValue(null);
    // Keep native suppression continuous even when the observer runs before
    // the parent's next draw pass. Clearing this state causes a native-layout flash.
    passPainter.setValue(suppressionApplied&&presentationVisible()?pendingCover:null);lastPainted=null;
    if(previouslyObserved&&presentationVisible()&&motion.phase()!=DiscTransition.Phase.WAITING&&!motion.closing()&&!motion.finished()){
     clearEntryTiming();
     trackGate.request(song,System.nanoTime());
     motion.trackChanged();trackPending.setValue(true);pose.setValue(motion.pose());
    }
    HostCoverBridge.invalidate(nativeRoot);
   }
   Object current=source();boolean available=HostCoverBridge.imageReady(current);
   boolean missing=coverAvailability.missing(song);
   if(changed||current!=observedSource||available!=observedReady||missing!=observedMissing){
    observedMissing=missing;
    observedSource=current;observedReady=available;
    report("SOURCE_STATE painter="+(current==null?"none":current.getClass().getName())+" decoded="+available+" failed="+failed);
    HostCoverBridge.invalidate(nativeRoot);return true;
   }
   return albumChanged;
  }
 }
 private static <T> MutableState<T> state(T value){return SnapshotStateKt.mutableStateOf(value,SnapshotStateKt.structuralEqualityPolicy());}
 public CdPageProbePlugin(PluginContext context){
  super(context);Path p=Paths.get(context.getPluginPath());if(p.getFileName()!=null&&p.getFileName().toString().endsWith(".spmod"))p=p.getParent();
  log=p.resolve("reborn-cd-sample.log");
 }
 @Override public synchronized void start(){
  if(running)return;
  report("HOST_DETECTED version="+getPluginContext().getSpwVersion()+" channel="+getPluginContext().getSpwChannel()+" os="+System.getProperty("os.name")+" java="+System.getProperty("java.version"));
  try{host=new HostBindings(HostCompatibility.verify(getPluginContext(),getClass().getClassLoader()),getClass().getClassLoader());report("HOST_PROFILE "+host.profile.version());}
  catch(IllegalStateException incompatible){report("HOST_REJECTED "+incompatible.getMessage());throw incompatible;}
  if(!WorkshopApi.manager().isPermissionGranted(PluginPermission.CLASS_TRANSFORM)||!WorkshopApi.manager().isPermissionGranted(PluginPermission.LIBRARY_READ))throw new IllegalStateException("Grant class-transform and library-read permissions");
  try{
   var painterDraw=PainterDrawBinding.METHOD;
   coverAvailability.reset();
   coverStatus=new HostCoverStatus(coverAvailability,this::currentSong);
   handles.add(coverStatus.install());
   discSettings=new DiscSettings(WorkshopApi.manager().createConfigManager(),this::applyDisplaySettings,
       this::artworkSource,()->albumKey(WorkshopApi.playback().getCurrentMediaItem()),this::prepareDefaultAppearance,this::labelSample);
   handles.add(WorkshopApi.hookRegistrar().replaceComposable(host.cover.getDeclaringClass().getName(),host.cover.getName(),host.caller,
    List.of("kotlin.jvm.functions.Function0","boolean","boolean","boolean","androidx.compose.ui.Modifier"),50,new ComposableHook(){
     @Override public void Content(UiHookCall call,Composer composer,int changed){content(call,composer);}
    }));
   handles.add(WorkshopApi.hookRegistrar().hookMethod(painterDraw.getDeclaringClass().getName(),painterDraw.getName(),
    PainterDrawBinding.PARAMETERS,new MethodHook(50){
     @Override public void before(MethodHookParam call){
      if(Boolean.TRUE.equals(rawSource.get())||!Boolean.TRUE.equals(enabled.getValue()))return;
      Frame owner=find(call.getThisObject(),(Long)call.getArgs()[1]);if(owner==null)return;
      // Capture the new native source even when only its cached layer redraws.
      owner.artwork.setValue(call.getThisObject());
      if(owner.passPainter.getValue()==call.getThisObject())call.setResult(null); // Exact observable source for this draw pass.
     }
    }));
   localDiscs=new LocalDiscService<>(this::currentSong,DiscImage::decode,()->discRevision.setValue(discRevision.getValue()+1),this::report);
   localDiscs.setNames(displaySettings.getValue().localNames());
   localDiscs.start();
   renderWarmupPending=true;renderWarmupDone=false;entryTimings.restart();
   generatedCdRaster=new GeneratedCdRaster(this::report);
   reelRasters=DiscSurfaceRaster.reel(this::report);
   reelTapeRaster=DiscSurfaceRaster.tape(this::report);
   vinylSurfaceRaster=DiscSurfaceRaster.vinyl(this::report);
   enabled.setValue(true);running=true;
   // Swing Timer actions execute on Compose Desktop's UI event thread; native
   // layout nodes must not be traversed by the local-image decode worker.
   sourceObserver=new javax.swing.Timer(100,event->observeSources());sourceObserver.start();
   animationClock=new javax.swing.Timer(16,event->animateFrames());animationClock.setCoalesce(true);animationClock.start();
   report("START "+getPluginContext().getPluginVersion()+" Host Binding; retained pixels are not readiness; entryStart=sourceReady; coverCdRaster=true; vinylSurfaceRaster=true; layeredReel=true; hooks="+handles.size());
  }catch(Throwable e){stop();throw new IllegalStateException("SelfDraw startup failed",e);}
 }
 @SuppressWarnings("unchecked")
 private void content(UiHookCall call,Composer composer){
  composer.startReplaceGroup(0x43445254);
  Object remembered=composer.rememberedValue();Frame frame;
  if(remembered==Composer.Companion.getEmpty()){
   frame=new Frame();composer.updateRememberedValue(frame);synchronized(frames){frames.add(new WeakReference<>(frame));}
  }else frame=(Frame)remembered;
  Density density=(Density)composer.consume(CompositionLocalsKt.getLocalDensity());
  frame.sheet=(PlayerSheetState)composer.consume(host.sheetLocal());
  final DiscSettings.Values display=displaySettings.getValue();
  final int amount=display.amount();final boolean front=display.front();
  var args=call.getArguments();frame.renderAllowed=(Boolean)args.get(3);
  boolean lyrics=(Boolean)args.get(1);
  frame.nativeLyrics=lyrics;
  if(!lyrics&&!frame.secondary){
   if(frame.coverOrigin==null)frame.coverOrigin=frame.captureCoverOrigin();
   frame.keepPrimaryCover.setValue(frame.coverOrigin!=null);
   frame.trackGate.reset();frame.motion.enter();frame.pose.setValue(frame.motion.pose());
  }
  // Native return paths may change the page before invoking our cover callback.
  // Retain the secondary visual until its disc is fully retracted.
  if(lyrics&&frame.secondary&&!frame.motion.finished()){
   frame.retainingReturn.setValue(true);
   frame.closeMotion(System.nanoTime(),()->{},amount>0);
  }
  boolean visualLyrics=lyrics&&!frame.retainingReturn.getValue();
  if(visualLyrics)frame.restoreNative();
  frame.secondary=!visualLyrics;
  Function0<Unit> nativeToggle=(Function0<Unit>)args.get(0);
  Function0<Unit> animatedToggle=()->{
   if(frame.secondary&&(frame.motion.closing()||frame.motion.finished()))return Unit.INSTANCE;
   if(!frame.secondary){
    frame.coverOrigin=frame.captureCoverOrigin();
    nativeToggle.invoke();return Unit.INSTANCE;
   }
   if(!frame.suppressionApplied){nativeToggle.invoke();return Unit.INSTANCE;}
   frame.closeMotion(System.nanoTime(),()->{
    if(!frame.nativeLyrics)nativeToggle.invoke();
   },amount>0);
   return Unit.INSTANCE;
  };
  boolean active=frame.presentationVisible();
  frame.syncVisibility(active);
  Viewport viewport=frame.viewport.getValue();
  if(frame.coverOrigin!=null&&!frame.coverOrigin.viewport().equals(viewport)){
   frame.coverOrigin=null;frame.keepPrimaryCover.setValue(false);
  }
  SelfDrawLayout plan=SelfDrawLayout.fit(viewport.width(),viewport.height(),density.getDensity(),!visualLyrics,visualLyrics?DiscExposure.DEFAULT:amount);
  frame.widgetWidth=plan.width();frame.widgetHeight=plan.height();
  if(!active||!plan.usable(density.getDensity()))frame.ready.setValue(false);
  Modifier box=SizeKt.fillMaxSize((Modifier)args.get(4),1f);
  box=OnGloballyPositionedModifierKt.onGloballyPositioned(box,c->{
   frame.viewportRoot=c;
   try{
    long size=size(c);
    var viewportSize=new Viewport((int)(size>>>32),(int)size);
    frame.viewport.setValue(viewportSize);
    warmupViewport=new WarmupViewport(viewportSize,density.getDensity());
   }catch(Exception e){report("VIEWPORT_ERROR "+e.getClass().getSimpleName());}
   return Unit.INSTANCE;
  });
  box=DrawModifierKt.drawWithContent(box,scope->{
   // Observes both enabled and ready. Turning the new widget on/off refreshes
   // cached native image layers so the fallback never stays blank/duplicated.
   var timing=frame.entryTiming;
   long handoffStarted=timing==null?0:System.nanoTime();
   Object selected=frame.paintSource();
   frame.passPainter.setValue(frame.suppress()?selected:null);
   boolean suppression=frame.passPainter.getValue()!=null;
   if(suppression&&frame.keepPrimaryCover.getValue())frame.keepPrimaryCover.setValue(false);
   String diagnostic="secondary="+frame.secondary+" allowed="+frame.renderAllowed+" ready="+frame.ready.getValue()+" failed="+frame.failed+" owned="+(frame.ownedCover!=null)+" placeholder="+(frame.pendingCover!=null)+" selected="+(selected!=null)+" phase="+frame.motion.phase()+" custom="+suppression;
   if(!diagnostic.equals(frame.handoffDiagnostic)){frame.handoffDiagnostic=diagnostic;report("DRAW_HANDOFF "+diagnostic);}
   if(suppression!=frame.suppressionApplied){frame.suppressionApplied=suppression;HostCoverBridge.invalidate(frame.nativeRoot);}
   if(timing!=null)timing.handoff(System.nanoTime()-handoffStarted);
   scope.drawContent();return Unit.INSTANCE;
  });
  final boolean show=active&&plan.usable(density.getDensity());
  BoxKt.Box(box,Alignment.Companion.getTopStart(),false,(boxScope,c,flags)->{
   c.startReplaceGroup(0x43444e41);
   Modifier nativeModifier=OnGloballyPositionedModifierKt.onGloballyPositioned(Modifier.Companion,coords->{frame.nativeRoot=coords;return Unit.INSTANCE;});
   // The sibling Canvas owns ALL secondary pixels, including its reflection.
   // Suppress native root content as a whole so cached native reflection layers
   // cannot survive the Painter hook and appear at the old cover position.
   nativeModifier=DrawModifierKt.drawWithContent(nativeModifier,nativeScope->{
    if(frame.passPainter.getValue()==null)nativeScope.drawContent();return Unit.INSTANCE;
   });
   boolean primaryCover=visualLyrics||frame.keepPrimaryCover.getValue()||frame.preparingNativeReturn.getValue();
   drawOriginal(args,nativeModifier,c,animatedToggle,primaryCover);
   c.endReplaceGroup();
   c.startReplaceGroup(0x43444f57);
   if(show){
    try{
     var origin=frame.coverOrigin;
     var translation=origin==null?null:CoverHandoff.translationFrom(origin.bounds(),plan);
     Modifier widget=CanvasWidget.modifier(plan,density.getDensity(),animatedToggle,layer->{
      // Move the whole layer so the incoming cover and reflection stay within its recording bounds.
      float remaining=CoverHandoff.remaining(frame.pose.getValue().scale());
      var alignment=frame.returnAdjustment.getValue();
      layer.setTranslationX(translation==null?0f:(translation.x()+alignment.x())*remaining);
      layer.setTranslationY(translation==null?0f:(translation.y()+alignment.y())*remaining);
      return Unit.INSTANCE;
     });
     widget=OnGloballyPositionedModifierKt.onGloballyPositioned(widget,coords->{
      frame.widgetRoot=coords;
      if(!frame.failed)try{
       long measured=size(coords);boolean fits=coords.isAttached()&&Math.abs((int)(measured>>>32)-plan.width())<=3&&Math.abs((int)measured-plan.height())<=3;
       frame.ready.setValue(fits);
       if(!fits){String error="expected="+plan.width()+"x"+plan.height()+" actual="+(int)(measured>>>32)+"x"+(int)measured;
        if(!error.equals(frame.lastBoundsError)){frame.lastBoundsError=error;report("SELF_DRAW_BOUNDS "+error);}
       }
      }catch(Exception e){frame.ready.setValue(false);}
      return Unit.INSTANCE;
     });
     CanvasKt.Canvas(widget,scope->{
      // Read before the early return: even an empty cached draw must remain
      // subscribed to the next source publication and recover without layout.
      DiscTransition.Pose motion=frame.pose.getValue();
      float angle=frame.rotationAngle.getValue();
      final String drawSong=frame.observedSong;
      String playbackSong;
      try{playbackSong=currentSong();}catch(Exception unavailable){playbackSong=null;}
      DrawCover selected=frame.drawCover(drawSong,playbackSong,motion.exposed());
      if(selected==null)return Unit.INSTANCE;
      Object painter=selected.painter();
      var timing=frame.entryTiming;
      boolean firstPaint=frame.lastPainted!=painter;
      boolean firstReveal=!frame.revealMeasured&&selected.exposed()>0&&amount>0;
      var phase=frame.motion.phase();
      boolean measureEntry=!frame.slowEntryDrawLogged
          &&(phase==DiscTransition.Phase.COVER||phase==DiscTransition.Phase.OPENING);
      boolean measureSpin=display.rotate()&&amount>0&&!frame.spinMeasured
          &&(phase==DiscTransition.Phase.OPENING||phase==DiscTransition.Phase.OPEN);
      long started=firstPaint||firstReveal||measureEntry||measureSpin||timing!=null?System.nanoTime():0;
      long reflectionNanos=0;
      try{
       discRevision.getValue(); // Observe async image publication, including same-song file replacement.
       LocalDiscService<DiscImage> service=localDiscs;
       var album=frame.observedAlbum.getValue();
       var appearance=display.appearanceFor(album);
       var vinylColor=resolveVinylColor(appearance.finish(),album,drawSong);
       var selectedReelColor=appearance.reelColor();
       var reelColor=appearance.style()==DiscSettings.Style.REEL&&selectedReelColor==DiscSettings.ReelColor.RANDOM
         ?reelRandom.resolve(album!=null?album:DiscSettings.AlbumKey.of(null,null,null,0,drawSong)):selectedReelColor;
       DiscImage cover=selected.cover();
       var bounds=cover==null||display.trimCover()==CoverWhiteBorders.Mode.OFF?null:cover.coverBounds(display.trimCover());
       boolean trim=bounds!=null;
       float aspect=cover==null?1f:trim?bounds.displayAspect():cover.aspect();
       var framing=cover==null?null:display.framingFor(album,appearance.style());
       var framedBounds=framing==null?null:cover.labelBounds(framing.crop());
       float framedAspect=framedBounds==null?aspect:(float)framedBounds.width()/framedBounds.height();
       var cdCache=generatedCdRaster;
       var cdKey=appearance.style()==DiscSettings.Style.CD&&cover==frame.ownedCover
           ?GeneratedCdRaster.Key.of(cover,framedBounds==null?bounds:framedBounds,framedAspect,RootRenderer.discRadius(plan.side(),aspect,trim)*2,framing):null;
       frame.cdRasterDrawn=false;
       if(selected.exposed()==0||cdKey==null)frame.cdRasterChoice=null;
       var reelCache=reelRasters;
       var tapeCache=reelTapeRaster;
       var vinylCache=vinylSurfaceRaster;
       frame.rasterDrawn=false;
       frame.tapeRasterDrawn=false;
       frame.vinylRasterDrawn=false;
       if(selected.exposed()==0||!appearance.style().vinyl())frame.vinylRasterChoice=null;
       if(selected.exposed()==0||appearance.style()!=DiscSettings.Style.REEL)frame.reelRasterChoice=null;
       RootRenderer.Disc disc=new RootRenderer.Disc(){
        @Override public boolean draw(androidx.compose.ui.graphics.drawscope.DrawScope target,float cx,float cy,float diameter){
         // Resolve custom artwork before offering any generated disc.
         try{if(service==null||service.drawResolved(drawSong,image->image.draw(target,cx,cy,diameter)))return true;}
         catch(Exception error){report("LOCAL_CD_DRAW_ERROR "+error.getClass().getSimpleName());return true;}
         if(cdKey!=null)return frame.drawPreparedCd(cdCache,cdKey,target,cx,cy,diameter);
         return appearance.style()==DiscSettings.Style.REEL
             &&frame.drawPreparedReel(reelCache,reelColor,angle,target,cx,cy,diameter);
        }
        @Override public boolean drawVinylSurface(androidx.compose.ui.graphics.drawscope.DrawScope target,float radius){
         return frame.drawPreparedVinyl(vinylCache,vinylColor,target,radius);
        }
        @Override public boolean drawReelTape(androidx.compose.ui.graphics.drawscope.DrawScope target,float radius){
         boolean drawn=tapeCache!=null&&tapeCache.draw(DiscSettings.ReelColor.DEFAULT,target,0,0,radius*2);
         frame.tapeRasterDrawn|=drawn;
         return drawn;
        }
       };
       var canvas=scope.getDrawContext().getCanvas();canvas.save();
       try{
        float travel=DiscExposure.fraction(amount)*.5f;
        float offset=plan.side()*travel*motion.centered();
        float pivot=plan.side()*(.5f+travel-travel*motion.centered());
        var handoff=frame.coverHandoff;
        if(handoff==null&&(phase==DiscTransition.Phase.WAITING||phase==DiscTransition.Phase.COVER)){
         // Use the same outgoing bounds for scaling and translation.
         if(origin==null)handoff=frame.measureCoverHandoff(painter,plan.side(),aspect);
         else{
          handoff=CoverHandoff.from(origin.bounds(),plan.side(),aspect);
          report("COVER_HANDOFF moving=true dx="+translation.x()+" dy="+translation.y()+" startScale="+handoff.scale());
         }
         frame.coverHandoff=handoff;
        }
        float shrink=CoverHandoff.remaining(motion.scale());
        boolean returningCover=shrink>0&&cover!=null&&origin!=null&&frame.preparingNativeReturn.getValue();
        // The native slot survives a song change. Fit the current artwork to
        // that slot even if its aspect ratio or the Canvas size changed.
        var alignment=frame.returnAdjustment.getValue();
        if(returningCover)handoff=CoverHandoff.fromSlot(origin.bounds(),plan.side(),alignment.sideDelta());
        float scale=handoff==null?motion.scale():handoff.animatedScale(shrink);
        float coverRadius=CoverHandoff.coverCornerRadius(scale,density.getDensity());
        canvas.translate(offset+pivot,plan.side()*.5f);
        canvas.scale(scale,scale);
        canvas.translate(-pivot,-plan.side()*.5f);
        RootRenderer.Source source=cover==null?null:new RootRenderer.Source(){
         public void draw(androidx.compose.ui.graphics.drawscope.DrawScope target,float w,float h)throws Exception{
          if(trim)cover.drawTrimmedCover(target,w,h,bounds);else cover.drawCover(target,w,h);
         }
         @Override public RootRenderer.FramedArtwork vinylLabel(){return framedArtwork();}
         @Override public RootRenderer.FramedArtwork discArtwork(){return framedArtwork();}
         private RootRenderer.FramedArtwork framedArtwork(){
          return framing==null?null:new RootRenderer.FramedArtwork(
              (target,w,h)->cover.drawTrimmedCover(target,w,h,framedBounds),framedAspect,framing);
         }
        };
        float exposed=selected.exposed();
        if(service!=null&&drawSong!=null&&Objects.equals(drawSong,playbackSong)){
         if(exposed==0&&amount>0&&!frame.motion.closing()&&!frame.motion.finished()){
          var status=service.artworkStatus(drawSong);
          if(cdCache!=null&&cdKey!=null&&status.resolved()&&status.fileName()==null)cdCache.prepare(cdKey);
          frame.prepareHiddenDisc(scope,exposed,amount,aspect,trim,appearance.style(),vinylColor,reelColor,
              androidx.compose.ui.graphics.SkiaBackedPath_skikoKt::Path,()->status);
         }
        }
        if(cover!=null)RootRenderer.paint(scope,aspect,source,disc,androidx.compose.ui.graphics.SkiaBackedPath_skikoKt::Path,exposed,amount,front,frame.frontDiscLayer,angle,appearance.style(),vinylColor,trim,reelColor,frame.coverCorners,coverRadius);
        else RootRenderer.draw(scope,painter,()->rawSource.set(true),()->rawSource.remove(),disc,exposed,amount,front,frame.frontDiscLayer,angle,frame.fallbackActive,appearance.style(),vinylColor,reelColor,frame.coverCorners,coverRadius);
        float gap=4*density.getDensity();
        float reflectedHeight=Math.max(0,plan.height()-plan.side()-gap);
        if(reflectedHeight>0||returningCover){
         var band=ReflectionRenderer.returning(plan.side(),RootRenderer.fittedHeight(plan.side(),aspect),
             reflectedHeight,gap,returningCover?shrink:0);
         // The recording layer allows out-of-bounds paint like the native cover.
         // Limit the longer return reflection in viewport coordinates without relayout.
         float translatedY=returningCover?(translation.y()+alignment.y())*shrink:0f;
         float clipBottom=returningCover
             ?plan.side()*.5f+(viewport.height()-Math.round(plan.top())-translatedY-plan.side()*.5f)/scale
             :plan.height();
         if(frame.reflection==null)frame.reflection=new ReflectionRenderer();
         long reflectionStarted=started==0?0:System.nanoTime();
         try{
          float mirrorExposure=exposed;
          if(!front&&!trim&&exposed>0&&service!=null){
           var artwork=service.artworkStatus(drawSong);
           if(artwork.resolved()&&artwork.fileName()==null){
            mirrorExposure=RootRenderer.reflectedExposure(scope,exposed,amount,band.sourceTop());
           }
          }
          float reflected=mirrorExposure;
          if(timing!=null&&reflected>0)timing.discReflection(started);
          frame.reflection.draw(scope,reflected==0?plan.side():plan.width(),band,clipBottom,()->{
           if(cover!=null)RootRenderer.paint(scope,aspect,source,disc,androidx.compose.ui.graphics.SkiaBackedPath_skikoKt::Path,reflected,amount,front,frame.frontDiscLayer,angle,appearance.style(),vinylColor,trim,reelColor,frame.coverCorners,coverRadius);
           else RootRenderer.draw(scope,painter,()->rawSource.set(true),()->rawSource.remove(),disc,reflected,amount,front,frame.frontDiscLayer,angle,frame.fallbackActive,appearance.style(),vinylColor,reelColor,frame.coverCorners,coverRadius);
          });
         }catch(Exception error){report("REFLECTION_ERROR "+error.getClass().getSimpleName());}
         finally{if(reflectionStarted!=0)reflectionNanos=System.nanoTime()-reflectionStarted;}
        }
       }finally{canvas.restore();}
       if(phase==DiscTransition.Phase.OPEN&&selected.exposed()==1f&&amount>0)frame.openDiscDrawn=true;
       if(timing!=null){
        timing.paint(phase,started,System.nanoTime()-started,reflectionNanos);
        if(phase==DiscTransition.Phase.OPEN){
         frame.clearEntryTiming();
         String summary=entryTimings.complete(timing);
         if(summary!=null)report(summary);
        }
       }
       if(measureSpin){
        if(phase==DiscTransition.Phase.OPEN&&angle!=0&&selected.exposed()>0){
         frame.spinMeasured=true;
         long gap=frame.previousSpinDraw==0?0:(started-frame.previousSpinDraw)/1_000;
         report("ROTATION_FIRST_DRAW style="+appearance.style()+" gapUs="+gap+" elapsedUs="+elapsedMicros(started)
             +" reflectionUs="+reflectionNanos/1_000+" reelRaster="+frame.rasterDrawn+" tapeRaster="+frame.tapeRasterDrawn+" cdRaster="+frame.cdRasterDrawn+" vinylRaster="+frame.vinylRasterDrawn);
        }
        frame.previousSpinDraw=started;
       }
       if(firstPaint){
        frame.lastPainted=painter;
        long drawMicros=elapsedMicros(started);
        report("SELF_DRAW prepared="+renderWarmupDone+" source="+painter.getClass().getName()
            +" widgetPx="+plan.width()+"x"+plan.height()+" coverSidePx="+plan.side()+" secondary="+plan.secondary()
            +" elapsedUs="+drawMicros+" canvasLayer="+CanvasWidget.hasDrawingLayer(frame.widgetRoot));
       }
       if(measureEntry){
        long drawMicros=elapsedMicros(started);
        if(drawMicros>=24_000){
         frame.slowEntryDrawLogged=true;
         report("ENTRY_SLOW_DRAW phase="+phase+" elapsedUs="+drawMicros+" reflectionUs="+reflectionNanos/1_000);
        }
       }
       if(firstReveal){
        frame.revealMeasured=true;
        long drawMicros=elapsedMicros(started);
        var artwork=service==null?null:service.artworkStatus(drawSong);
        String artworkKind=artwork==null||!artwork.resolved()?"unresolved":artwork.fileName()==null?"generated":"local";
        report("FIRST_REVEAL_DRAW elapsedUs="+drawMicros+" reflectionUs="+reflectionNanos/1_000
            +" style="+appearance.style()+" artwork="+artworkKind+" reelRaster="+frame.rasterDrawn+" tapeRaster="+frame.tapeRasterDrawn+" cdRaster="+frame.cdRasterDrawn+" vinylRaster="+frame.vinylRasterDrawn);
       }
      }catch(Throwable error){frame.clearEntryTiming();frame.clearCover();frame.passPainter.setValue(null);frame.rejectedSource.setValue(painter);HostCoverBridge.invalidate(frame.nativeRoot);report("SELF_DRAW_SOURCE_ERROR native fallback until a fresh source: "+error.getClass().getSimpleName());}
      return Unit.INSTANCE;
     },c,0);
     c.startReplaceGroup(0x43444546);
     if(frame.entryFramesRequested.getValue())
      EffectsKt.LaunchedEffect(frame,(scope,completion)->frame.driveEntryFrames(completion),c,0);
     c.endReplaceGroup();
    }catch(Exception error){frame.failed=true;frame.ready.setValue(false);report("WIDGET_ERROR "+error.getClass().getSimpleName());}
   }
   c.endReplaceGroup();return Unit.INSTANCE;
  },composer,0,0);
  composer.endReplaceGroup();
 }
 private Frame find(Object painter,long drawSize){
  List<Frame> current=new ArrayList<>();
  synchronized(frames){for(var it=frames.iterator();it.hasNext();){Frame f=it.next().get();if(f==null)it.remove();else current.add(f);}}
  Frame match=null;
  for(Frame frame:current)if(frame.owns(painter,drawSize)){if(match!=null&&match!=frame)return null;match=frame;}
  return match;
 }
 private VinylLabelEditor.Sample labelSample(DiscSettings.AlbumKey album,DiscSettings.VinylColor color){
  try{
   String song=currentSong();
   if(song==null||!Objects.equals(album,currentAlbumKey(song)))return null;
   var image=visibleCoverPreview(song);
   var status=coverStatus;
   if(image==null&&status!=null)image=status.labelPreview(song);
   if(image!=null&&Objects.equals(song,currentSong())&&Objects.equals(album,currentAlbumKey(song)))
    return new VinylLabelEditor.Sample(image,resolveVinylColor(color,album,song).shade(65));
  }catch(Exception|LinkageError error){report("LABEL_PREVIEW_ERROR "+error.getClass().getSimpleName());}
  return null;
 }
 private java.awt.image.BufferedImage visibleCoverPreview(String song){
  synchronized(frames){
   for(var reference:frames){
    var frame=reference.get();
    if(frame==null||!Objects.equals(song,frame.observedSong)||frame.fallbackActive||frame.pendingCover!=null)continue;
    Object painter=frame.passPainter.getValue();
    if(painter==null)continue;
    try{
     if(!frame.presentationVisible())continue;
     var owned=frame.ownedCover;
     if(owned!=null&&painter==frame.ownedSource){
      var preview=owned.labelPreview();
      if(preview!=null)return preview;
     }
     // An active pass can draw the host painter directly before we own a copy.
     if(HostCoverBridge.imageReady(painter)){
      try(var cover=HostCoverBridge.capture(painter)){
       var preview=cover.labelPreview();
       if(preview!=null)return preview;
      }
     }
    }catch(Exception|LinkageError error){report("VISIBLE_COVER_PREVIEW_ERROR "+error.getClass().getSimpleName());}
   }
  }
  return null;
 }
 private DiscSettings.ArtworkSource artworkSource(){
  try{
   String song=currentSong();
   if(song==null||song.isBlank())return new DiscSettings.ArtworkSource("未播放曲目");
   var service=localDiscs;
   if(service==null)return new DiscSettings.ArtworkSource("盘面来源暂不可用");
   var artwork=service.artworkStatus(song);
   if(!artwork.resolved())return new DiscSettings.ArtworkSource("正在查找盘面图片…");
   if(artwork.fileName()!=null)return new DiscSettings.ArtworkSource("使用自定义图片 "+artwork.fileName(),DiscSettings.ArtworkKind.CUSTOM);
   var display=displaySettings.getValue();
   var album=currentAlbumKey(song);
   var appearance=display.appearanceFor(album);
   String source=album!=null&&display.albumStyles().containsKey(album)?"专辑设置":"全局默认";
   String style=switch(appearance.style()){
    case COLORED_VINYL -> appearance.color().label+"彩胶唱片";
    case REEL -> appearance.reelColor().label+"卷盘";
    default -> appearance.style().label;
   };
   String description=source+" · "+style;
   if(!appearance.style().reel()&&coverAvailability.missing(song))description+="（无封面，显示空白盘面）";
   return new DiscSettings.ArtworkSource(description,DiscSettings.ArtworkKind.GENERATED);
  }catch(RuntimeException unavailable){return new DiscSettings.ArtworkSource("盘面来源暂不可用");}
 }
 private boolean discResolved(){try{return localDiscs!=null&&discResolved(currentSong());}catch(Exception unavailable){return false;}}
 private boolean discResolved(String song){return localDiscs!=null&&localDiscs.isResolved(song);}
 private void prepareDefaultAppearance(DiscSettings.Appearance appearance){
  // The settings dialog already saves on a worker. Finish preparation before
  // publishing the new style so an immediate open can use its warmed shaders.
  var lifecycle=reelRasters;
  if(!running||lifecycle==null)return;
  if(displaySettings.getValue().amount()>0&&appearance.style()==DiscSettings.Style.REEL
      &&appearance.reelColor()!=DiscSettings.ReelColor.RANDOM)lifecycle.prepare(appearance.reelColor());
  var viewport=warmupViewport;
  var target=viewport==null?RenderWarmup.Target.SAMPLE:viewport.target(displaySettings.getValue().amount());
  long started=System.nanoTime();
  try{
   var finishes=RenderWarmup.run(appearance,target);
   if(running&&lifecycle==reelRasters)preparedAppearance=new PreparedAppearance(appearance,finishes);
   report("SETTINGS_RENDER_WARMUP style="+appearance.style()+" width="+target.width()+" height="+target.height()
       +" elapsedUs="+elapsedMicros(started));
  }catch(Exception|LinkageError unavailable){
   report("SETTINGS_RENDER_WARMUP_SKIPPED style="+appearance.style()+" "+unavailable.getClass().getSimpleName());
  }
 }
 private void applyDisplaySettings(DiscSettings.Values value){
  var previous=displaySettings.getValue();
  if(value.rotate()&&!previous.rotate()){
   long started=System.nanoTime();
   if(playbackState.prepare(this::report))report("ROTATION_BINDING elapsedUs="+elapsedMicros(started));
  }
  if(!previous.appearance().equals(value.appearance())){
   var prepared=preparedAppearance;
   boolean warmed=prepared!=null&&prepared.appearance().equals(value.appearance());
   if(warmed){
    preparedAppearance=null;
    RootRenderer.adoptPrepared(prepared.finishes());
   }
   int series=entryTimings.restart();
   report("DEFAULT_APPEARANCE_CHANGED series="+series+" from="+previous.appearance().style()+" to="+value.appearance().style()
       +" warmed="+warmed+" rotate="+value.rotate());
   var raster=reelRasters;
   var tape=reelTapeRaster;
   var appearance=value.appearance();
   if(tape!=null&&value.amount()>0&&appearance.style().reel())tape.prepare(DiscSettings.ReelColor.DEFAULT);
   var vinyl=vinylSurfaceRaster;
   if(vinyl!=null&&value.amount()>0&&appearance.style().vinyl()&&appearance.finish()!=DiscSettings.VinylColor.RANDOM)
    vinyl.prepare(appearance.finish());
   if(raster!=null&&value.amount()>0&&appearance.style()==DiscSettings.Style.REEL
       &&appearance.reelColor()!=DiscSettings.ReelColor.RANDOM)raster.prepare(appearance.reelColor());
  }
  if(localDiscs!=null)localDiscs.setNames(value.localNames());
  displaySettings.setValue(value);
  report("DISC_SETTINGS amount="+value.amount()+" front="+value.front()+" rotate="+value.rotate()+" vinyl="+value.vinyl());
 }
 private void observeSources(){
  if(!running)return;
  try{
   var item=WorkshopApi.playback().getCurrentMediaItem();
   String song=item==null?null:item.getPath();
   var album=albumKey(item);
   if(localDiscs!=null)localDiscs.request(song);
   boolean changed=false;
   boolean entryVisible=false;
   synchronized(frames){for(var it=frames.iterator();it.hasNext();){
    Frame frame=it.next().get();if(frame==null){it.remove();continue;}
    if(frame.presentationVisible())entryVisible=true;
    if(frame.secondary&&frame.nativeRoot!=null&&frame.nativeRoot.isAttached()){
     changed|=frame.observe(song,album);
     // Bootstrap only when enabled after the host's last completed read.
     // One host refresh per unknown track; never repeat or infer absence by timeout.
     if(coverStatus!=null&&frame.observedSource==null&&frame.ownedCover==null&&coverAvailability.refreshNeeded(song,System.nanoTime()))coverStatus.refreshCurrent();
    }
   }}
   if(renderWarmupPending&&!entryVisible)warmRenderPath();
   if(!entryVisible){
    var wholeReel=reelRasters;
    var tape=reelTapeRaster;
    var settings=displaySettings.getValue();
    var appearance=settings.appearanceFor(album);
    var vinyl=vinylSurfaceRaster;
    if(vinyl!=null&&settings.amount()>0&&appearance.style().vinyl()){
     vinyl.prepare(resolveVinylColor(appearance.finish(),album,song));
    }
    if(tape!=null&&settings.amount()>0&&appearance.style().reel())tape.prepare(DiscSettings.ReelColor.DEFAULT);
    if(wholeReel!=null&&settings.amount()>0&&appearance.style()==DiscSettings.Style.REEL
        &&appearance.reelColor()!=DiscSettings.ReelColor.RANDOM)wholeReel.prepare(appearance.reelColor());
   }
   if(changed){sourceRevision.setValue(sourceRevision.getValue()+1);report("SOURCE_REFRESH");}
  }catch(Exception error){report("SOURCE_OBSERVER_ERROR "+error.getClass().getSimpleName());}
 }
 private void warmRenderPath(){
  renderWarmupPending=false;
  long started=System.nanoTime();
  try{
   RenderWarmup.run(displaySettings.getValue());
   renderWarmupDone=true;
   report("RENDER_WARMUP elapsedUs="+elapsedMicros(started));
  }catch(Exception|LinkageError unavailable){
   report("RENDER_WARMUP_SKIPPED "+unavailable.getClass().getSimpleName());
  }
 }
 private void animateFrames(){
  if(!running)return;
  long now=System.nanoTime();
  MediaItem item;
  try{item=WorkshopApi.playback().getCurrentMediaItem();}catch(Exception unavailable){return;}
  String song=item==null?null:item.getPath();
  String cueSnapshot=CuePlaybackTrack.snapshot();
  if(localDiscs!=null){localDiscs.request(song);localDiscs.resolveSlowLookup(song);}
  boolean sourceChanged=false;
  var display=displaySettings.getValue();
  boolean rotate=display.rotate();
  Boolean playing=null;
  synchronized(frames){for(var it=frames.iterator();it.hasNext();){
   Frame frame=it.next().get();if(frame==null){it.remove();continue;}
   // Expanded target alone is insufficient: animation must not run while the
   // host is still moving from/to the mini player (including interrupted gestures).
   // Composition and timer must sample the same visibility predicate. A short
   // native-node detach during AnimatedContent is not a sheet close/reopen.
   boolean visible=frame.presentationVisible();
   frame.syncVisibility(visible);
   if(!visible){frame.rotationAngle.setValue(frame.rotation.sample(song,rotate,false,false,now));continue;}
   frame.refreshReady();
   if(frame.nativeRoot==null||!frame.nativeRoot.isAttached()){
    if(frame.motion.closing()||frame.preparingNativeReturn.getValue())frame.advanceMotion(now);
    frame.rotationAngle.setValue(frame.rotation.sample(song,rotate,false,false,now));continue;
   }
   try{
    if(frame.entryTiming!=null)frame.entryTiming.clock(now);
    frame.motion.setSpeed(display.motionSpeed());
    if(frame.motion.phase()==DiscTransition.Phase.IDLE){frame.motion.enter();frame.pose.setValue(frame.motion.pose());}
    var album=Objects.equals(song,frame.observedSong)?frame.observedAlbum.getValue():albumKey(item);
    sourceChanged|=frame.observe(song,album);
    boolean switched=frame.trackGate.observe(song,now);
    boolean cueChanged=frame.cueTrack.changed(song,cueSnapshot);
    if((switched||cueChanged)&&!frame.motion.closing()&&!frame.motion.finished()){
     if(cueChanged){
      frame.trackGate.request(song,now);
      frame.rotation.reset();frame.rotationAngle.setValue(0f);
     }
     frame.clearEntryTiming();
     frame.spinMeasured=false;frame.previousSpinDraw=0;
     frame.motion.trackChanged();frame.trackPending.setValue(true);frame.pose.setValue(frame.motion.pose());
     report(cueChanged?"TRACK_ENTRY_PENDING cue=true":"TRACK_ENTRY_PENDING");
    }
    Object source=frame.passPainter.getValue();
    if(frame.motion.phase()==DiscTransition.Phase.TRACK_WAIT){
     if(frame.trackGate.ready(song,source,frame.fallbackActive||frame.ownedCover!=null||HostCoverBridge.imageReady(source),discResolved(song),now)){
      frame.motion.trackReady(now);frame.trackPending.setValue(false);report("TRACK_ENTRY_START");
     }
    }else if(source!=null&&Boolean.TRUE.equals(frame.ready.getValue())){
     // Start at source and layout readiness, as in v1.9.0.
     frame.motion.ready(now);
    }
    frame.advanceFromTimer(now);
    boolean canRotate=rotate&&song!=null&&frame.motion.phase()==DiscTransition.Phase.OPEN&&frame.openDiscDrawn
      &&(frame.fallbackActive||frame.ownedCover!=null)&&display.amount()>0&&discResolved(song);
    if(canRotate&&playing==null)playing=playbackState.playing(this::report);
    frame.rotationAngle.setValue(frame.rotation.sample(song,rotate,canRotate&&playing,canRotate,now,display.rotationSpeed().rotationPeriod));
   }catch(Exception error){frame.rotation.sample(song,rotate,false,false,now);frame.motion.reset();frame.clearReturnHandoff();frame.pose.setValue(frame.motion.pose());report("ANIMATION_ERROR "+error.getClass().getSimpleName());}
  }}
  // The faster animation clock may see a new source before sourceObserver.
  // Publish that observation too, so a suppressed cached native layer cannot
  // leave the parent Canvas waiting for a change the observer already missed.
  if(sourceChanged)sourceRevision.setValue(sourceRevision.getValue()+1);
 }
 private DiscSettings.AlbumKey currentAlbumKey(String song){
  if(song==null||song.isBlank())return null;
  try{
   var item=WorkshopApi.playback().getCurrentMediaItem();
   if(item!=null&&Objects.equals(song,item.getPath()))
    return albumKey(item);
  }catch(RuntimeException|LinkageError unavailable){ /* Fall back to the current path. */ }
  return DiscSettings.AlbumKey.of(null,null,null,0,song);
 }
 private static DiscSettings.AlbumKey albumKey(MediaItem item){
  if(item==null||item.getPath()==null||item.getPath().isBlank())return null;
  return DiscSettings.AlbumKey.of(item.getAlbum(),item.getAlbumArtist(),item.getArtist(),item.getYear(),item.getPath());
 }
 private DiscSettings.VinylColor resolveVinylColor(DiscSettings.VinylColor color,DiscSettings.AlbumKey album,String song){
  if(color!=DiscSettings.VinylColor.RANDOM)return color;
  return album==null?color.resolve(null,null,null,0,song,vinylColorSalt)
      :color.resolve(album.album(),album.artist(),album.artist(),album.year(),album.path(),vinylColorSalt);
 }
 private String currentSong(){var item=WorkshopApi.playback().getCurrentMediaItem();return item==null?null:item.getPath();}
 private static long size(LayoutCoordinates c)throws Exception{return (Long)PainterDrawBinding.LAYOUT_SIZE.invoke(c);}
 @SuppressWarnings("unchecked") private void drawOriginal(List<Object> a,Modifier modifier,Composer c,Function0<Unit> toggle,boolean lyrics){
  host.drawOriginal(toggle,lyrics,(Boolean)a.get(2),(Boolean)a.get(3),modifier,c);
 }
 @Override public synchronized void stop(){
  enabled.setValue(false);running=false;coverAvailability.reset();coverStatus=null;
  warmupViewport=null;preparedAppearance=null;
  if(animationClock!=null){animationClock.stop();animationClock=null;}
  if(discSettings!=null){try{discSettings.close();}catch(Exception error){report("SETTINGS_CLOSE_WARNING "+error.getClass().getSimpleName());}discSettings=null;}
  synchronized(frames){for(var ref:frames){Frame frame=ref.get();if(frame!=null)frame.restoreNative();}}
  if(sourceObserver!=null){sourceObserver.stop();sourceObserver=null;}
  if(generatedCdRaster!=null){generatedCdRaster.close();generatedCdRaster=null;}
  if(reelRasters!=null){reelRasters.close();reelRasters=null;}
  if(reelTapeRaster!=null){reelTapeRaster.close();reelTapeRaster=null;}
  if(vinylSurfaceRaster!=null){vinylSurfaceRaster.close();vinylSurfaceRaster=null;}
  if(localDiscs!=null){localDiscs.close();localDiscs=null;}
  for(int i=handles.size()-1;i>=0;i--)try{handles.get(i).close();}catch(Throwable e){report("UNHOOK_WARNING "+e.getClass().getSimpleName());}
  handles.clear();rawSource.remove();report("STOP SelfDraw disabled; native fallback");
 }
 @Override public void update(){stop();}@Override public void delete(){stop();}
 private static long elapsedMicros(long started){return (System.nanoTime()-started)/1_000;}
 private void report(String message){
  StringBuilder safe=new StringBuilder();
  message.codePoints().limit(512).forEach(cp->safe.appendCodePoint(
   Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||cp==0x2028||cp==0x2029?' ':cp));
  byte[] line=(Instant.now()+" "+safe+System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  logWriter.execute(()->{
   try{
    Files.createDirectories(log.toAbsolutePath().getParent());
    try(var channel=Files.newByteChannel(log,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
     long size=channel.size();
     if(size+line.length>LOG_MAX_BYTES){channel.truncate(0);size=0;}
     channel.position(size);
     var buffer=java.nio.ByteBuffer.wrap(line);
     while(buffer.hasRemaining())channel.write(buffer);
    }
   }catch(Exception ignored){System.err.println(safe);}
  });
 }
}
