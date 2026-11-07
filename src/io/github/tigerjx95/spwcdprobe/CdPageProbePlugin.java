package io.github.tigerjx95.spwcdprobe;
import androidx.compose.runtime.*;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.Alignment;
import androidx.compose.ui.draw.DrawModifierKt;
import androidx.compose.ui.layout.*;
import androidx.compose.ui.platform.CompositionLocalsKt;
import androidx.compose.ui.unit.Density;
import androidx.compose.foundation.CanvasKt;
import androidx.compose.foundation.layout.*;
import com.xuncorp.spw.workshop.api.*;
import com.xuncorp.spw.workshop.api.hook.*;
import com.xuncorp.voxzen.ui.component.PlayerSheetState;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

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
 private final Object logLock=new Object();
 private static final long LOG_MAX_BYTES=256L*1024;
 private volatile boolean running;
 private javax.swing.Timer sourceObserver,animationClock;
 private final MutableState<Long> sourceRevision=state(0L);
 private final MutableState<Long> discRevision=state(0L);
 private LocalDiscService<DiscImage> localDiscs;
 private final MutableState<DiscSettings.Values> displaySettings=state(DiscSettings.DEFAULT);
 private DiscSettings discSettings;
 private final PlaybackStateReader playbackState=new PlaybackStateReader();
 private record Viewport(int width,int height){}
 record DrawCover(Object painter, DiscImage cover, float exposed) {}
 private final class Frame {
  final MutableState<Viewport> viewport=state(new Viewport(0,0));
  final MutableState<Object> artwork=state(null);
  final MutableState<Object> rejectedSource=state(null);
  final MutableState<Boolean> ready=state(false);
  volatile LayoutCoordinates nativeRoot;
  LayoutCoordinates widgetRoot;
  float widgetWidth,widgetHeight;
  PlayerSheetState sheet;
  boolean renderAllowed=true,failed,suppressionApplied,secondary;
  Object lastPainted;
  final MutableState<Object> passPainter=state(null);
  Object observedSource;
  boolean observedReady,observedMissing,fallbackActive;
  String observedSong;
  ReflectionRenderer reflection;
  final FrontDiscLayer frontDiscLayer=new FrontDiscLayer();
  final DiscRotation rotation=new DiscRotation();
  final MutableState<Float> rotationAngle=state(0f);
  final MutableState<Boolean> retainingReturn=state(false);
  final RevealCycle revealCycle=new RevealCycle();
  final CoverSelection coverSelection=new CoverSelection();
  final TrackEntryGate trackGate=new TrackEntryGate();
  final MutableState<Boolean> trackPending=state(false);
  final DiscTransition motion=new DiscTransition();
  final MutableState<DiscTransition.Pose> pose=state(motion.pose());
  void freezeRotation(long now){rotationAngle.setValue(rotation.freeze(now));}
  void closeMotion(long now,Runnable done,boolean visibleDisc){
   freezeRotation(now);
   motion.setSpeed(displaySettings.getValue().motionSpeed());
   motion.close(now,done,visibleDisc);pose.setValue(motion.pose());
  }
  void advanceMotion(long now){
   boolean changed=motion.tick(now);
   // Widget width conservatively bounds both cover centering and disc travel,
   // including full exposure. Never advance navigation during the brake.
   changed=motion.finishSubpixelReturn(now,widgetWidth)||changed;
   if(changed)pose.setValue(motion.pose());
  }
  String lastBoundsError="";
  DiscImage ownedCover,pendingCover;
  Object ownedSource,lastCaptureFailure;
  String handoffDiagnostic="";
  void clearCover(){
   fallbackActive=false;
   if(ownedCover!=null)ownedCover.close();ownedCover=null;
   if(pendingCover!=null)pendingCover.close();pendingCover=null;
   ownedSource=null;lastCaptureFailure=null;
  }
  void awaitCover(){
   // Retain only the pixels already on screen, in the same Canvas geometry.
   // This is a visual placeholder, never proof of current-track readiness.
   if(ownedCover!=null){
    if(pendingCover!=null)pendingCover.close();
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
  Object paintSource(){
   if(!presentationVisible())return null;
   String song;try{song=currentSong();}catch(Exception unavailable){return null;}
   if(!Objects.equals(song,observedSong)){
    observe(song);
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
    DiscImage copy=HostCoverBridge.capture(live);
    DiscImage previous=ownedCover;ownedCover=copy;ownedSource=live;
    if(previous!=null)previous.close();
    if(pendingCover!=null)pendingCover.close();pendingCover=null;
    report("COVER_OWNED source="+System.identityHashCode(live));
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
  void restoreNative(){
   clearCover();rotation.reset();rotationAngle.setValue(0f);
   secondary=false;ready.setValue(false);widgetRoot=null;revealCycle.reset();motion.reset();trackGate.reset();trackPending.setValue(false);retainingReturn.setValue(false);pose.setValue(motion.pose());
   if(suppressionApplied)HostCoverBridge.invalidate(nativeRoot);
   passPainter.setValue(null);suppressionApplied=false;
  }
  void syncVisibility(boolean visible){
   RevealCycle.Edge edge=revealCycle.sample(visible);
   if(edge==RevealCycle.Edge.NONE)return;
   if(edge==RevealCycle.Edge.HIDDEN){freezeRotation(System.nanoTime());clearCover();}
   // No old pass, pending return or track request may leak into the next presentation.
   motion.reset();trackGate.reset();trackPending.setValue(false);retainingReturn.setValue(false);
   passPainter.setValue(null);suppressionApplied=false;
   if(edge==RevealCycle.Edge.SHOWN){motion.enter();report("PRESENTATION_READY restart cover-first entry");}
   pose.setValue(motion.pose());HostCoverBridge.invalidate(nativeRoot);
  }
  List<HostCoverBridge.Image> images(){
   var root=nativeRoot;return root!=null&&root.isAttached()?HostCoverBridge.images(root):List.of();
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

  boolean observe(String song){
   coverAvailability.observe(song,System.nanoTime());
   coverSelection.song(song);
   boolean changed=!Objects.equals(song,observedSong);
   if(changed){
    fallbackActive=false;rotation.reset();rotationAngle.setValue(0f);
    boolean previouslyObserved=observedSong!=null;
    if(song!=null&&!song.isBlank())awaitCover();else clearCover();
    observedSong=song;failed=false;rejectedSource.setValue(null);artwork.setValue(null);
    // Keep native suppression continuous even when the observer runs before
    // the parent's next draw pass. Clearing this state causes a native-layout flash.
    passPainter.setValue(suppressionApplied&&presentationVisible()?pendingCover:null);lastPainted=null;
    if(previouslyObserved&&presentationVisible()&&motion.phase()!=DiscTransition.Phase.WAITING&&!motion.closing()&&!motion.finished()){
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
   return false;
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
  catch(IllegalStateException incompatible){report("HOST_REJECTED "+incompatible.getClass().getSimpleName());throw incompatible;}
  if(!WorkshopApi.manager().isPermissionGranted(PluginPermission.CLASS_TRANSFORM)||!WorkshopApi.manager().isPermissionGranted(PluginPermission.LIBRARY_READ))throw new IllegalStateException("Grant class-transform and library-read permissions");
  try{
   var painterDraw=PainterDrawBinding.METHOD;
   coverAvailability.reset();
   coverStatus=new HostCoverStatus(coverAvailability,this::currentSong);
   handles.add(coverStatus.install());
   discSettings=new DiscSettings(WorkshopApi.manager().createConfigManager(),value->{
    displaySettings.setValue(value);report("DISC_SETTINGS amount="+value.amount()+" front="+value.front()+" rotate="+value.rotate()+" vinyl="+value.vinyl());
   });
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
   localDiscs.start();
   enabled.setValue(true);running=true;
   // Swing Timer actions execute on Compose Desktop's UI event thread; native
   // layout nodes must not be traversed by the local-image decode worker.
   sourceObserver=new javax.swing.Timer(100,event->observeSources());sourceObserver.start();
   animationClock=new javax.swing.Timer(16,event->animateFrames());animationClock.setCoalesce(true);animationClock.start();
   report("START 1.5.0 Host Binding; retained pixels are not readiness; hooks="+handles.size());
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
  if(!lyrics&&!frame.secondary){frame.trackGate.reset();frame.motion.enter();frame.pose.setValue(frame.motion.pose());}
  // Native return paths may change the page before invoking our cover callback.
  // Retain the secondary visual until its disc is fully retracted.
  if(lyrics&&frame.secondary&&!frame.motion.finished()){
   frame.retainingReturn.setValue(true);
   frame.closeMotion(System.nanoTime(),()->frame.retainingReturn.setValue(false),amount>0);
  }
  boolean visualLyrics=lyrics&&!frame.retainingReturn.getValue();
  if(visualLyrics)frame.restoreNative();
  frame.secondary=!visualLyrics;
  Function0<Unit> nativeToggle=(Function0<Unit>)args.get(0);
  Function0<Unit> animatedToggle=()->{
   if(frame.secondary&&(frame.motion.closing()||frame.motion.finished()))return Unit.INSTANCE;
   if(!frame.secondary||!frame.suppressionApplied){nativeToggle.invoke();return Unit.INSTANCE;}
   frame.closeMotion(System.nanoTime(),()->{
    frame.pose.setValue(frame.motion.pose());nativeToggle.invoke();
   },amount>0);
   return Unit.INSTANCE;
  };
  boolean active=frame.presentationVisible();
  frame.syncVisibility(active);
  Viewport viewport=frame.viewport.getValue();
  SelfDrawLayout plan=SelfDrawLayout.fit(viewport.width(),viewport.height(),density.getDensity(),!visualLyrics,visualLyrics?DiscExposure.DEFAULT:amount);
  frame.widgetWidth=plan.width();frame.widgetHeight=plan.height();
  if(!active||!plan.usable(density.getDensity()))frame.ready.setValue(false);
  Modifier box=SizeKt.fillMaxSize((Modifier)args.get(4),1f);
  box=OnGloballyPositionedModifierKt.onGloballyPositioned(box,c->{
   try{long size=size(c);frame.viewport.setValue(new Viewport((int)(size>>>32),(int)size));}catch(Exception e){report("VIEWPORT_ERROR "+e.getClass().getSimpleName());}
   return Unit.INSTANCE;
  });
  box=DrawModifierKt.drawWithContent(box,scope->{
   // Observes both enabled and ready. Turning the new widget on/off refreshes
   // cached native image layers so the fallback never stays blank/duplicated.
   Object selected=frame.paintSource();
   frame.passPainter.setValue(frame.suppress()?selected:null);
   boolean suppression=frame.passPainter.getValue()!=null;
   String diagnostic="secondary="+frame.secondary+" allowed="+frame.renderAllowed+" ready="+frame.ready.getValue()+" failed="+frame.failed+" owned="+(frame.ownedCover!=null)+" placeholder="+(frame.pendingCover!=null)+" selected="+(selected!=null)+" phase="+frame.motion.phase()+" custom="+suppression;
   if(!diagnostic.equals(frame.handoffDiagnostic)){frame.handoffDiagnostic=diagnostic;report("DRAW_HANDOFF "+diagnostic);}
   if(suppression!=frame.suppressionApplied){frame.suppressionApplied=suppression;HostCoverBridge.invalidate(frame.nativeRoot);}
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
   drawOriginal(args,nativeModifier,c,animatedToggle,visualLyrics);
   c.endReplaceGroup();
   c.startReplaceGroup(0x43444f57);
   if(show){
    try{
     Modifier widget=CanvasWidget.modifier(plan,density.getDensity(),animatedToggle);
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
      try{
       discRevision.getValue(); // Observe async image publication, including same-song file replacement.
       RootRenderer.Disc disc=(target,cx,cy,diameter)->{
        LocalDiscService<DiscImage> service=localDiscs;
        String song;
        try{song=currentSong();}catch(Exception unavailable){return true;}
        if(!Objects.equals(drawSong,song))return true;
        // true also means pending: draw cover only, never a temporary silver CD.
        try{return service==null||service.drawResolved(song,image->image.draw(target,cx,cy,diameter));}
        catch(Exception error){if(service!=null)service.discard(song);report("LOCAL_CD_DRAW_ERROR "+error.getClass().getSimpleName());return true;}
       };
       var canvas=scope.getDrawContext().getCanvas();canvas.save();
       try{
        float travel=DiscExposure.fraction(amount)*.5f;
        float offset=plan.side()*travel*motion.centered();
        float pivot=plan.side()*(.5f+travel-travel*motion.centered());
        canvas.translate(offset+pivot,plan.side()*.5f);
        canvas.scale(motion.scale(),motion.scale());
        canvas.translate(-pivot,-plan.side()*.5f);
        DiscImage cover=selected.cover();
        float exposed=selected.exposed();
        if(cover!=null)RootRenderer.paint(scope,cover.aspect(),cover::drawCover,disc,androidx.compose.ui.graphics.SkiaBackedPath_skikoKt::Path,exposed,amount,front,frame.frontDiscLayer,angle,display.vinyl());
        else RootRenderer.draw(scope,painter,()->rawSource.set(true),()->rawSource.remove(),disc,exposed,amount,front,frame.frontDiscLayer,angle,frame.fallbackActive,display.vinyl());
        float gap=4*density.getDensity(),reflectedHeight=plan.height()-plan.side()-gap;
        if(reflectedHeight>0){
         if(frame.reflection==null)frame.reflection=new ReflectionRenderer();
         try{frame.reflection.draw(scope,plan.side(),plan.width(),reflectedHeight,gap,()->{
          if(cover!=null)RootRenderer.paint(scope,cover.aspect(),cover::drawCover,disc,androidx.compose.ui.graphics.SkiaBackedPath_skikoKt::Path,exposed,amount,front,frame.frontDiscLayer,angle,display.vinyl());
          else RootRenderer.draw(scope,painter,()->rawSource.set(true),()->rawSource.remove(),disc,exposed,amount,front,frame.frontDiscLayer,angle,frame.fallbackActive,display.vinyl());
         });}
         catch(Exception error){report("REFLECTION_ERROR "+error.getClass().getSimpleName());}
        }
       }finally{canvas.restore();}
       if(frame.lastPainted!=painter){frame.lastPainted=painter;report("SELF_DRAW source="+painter.getClass().getName()+" widgetPx="+plan.width()+"x"+plan.height()+" coverSidePx="+plan.side()+" secondary="+plan.secondary());}
      }catch(Throwable error){frame.clearCover();frame.passPainter.setValue(null);frame.rejectedSource.setValue(painter);HostCoverBridge.invalidate(frame.nativeRoot);report("SELF_DRAW_SOURCE_ERROR native fallback until a fresh source: "+error.getClass().getSimpleName());}
      return Unit.INSTANCE;
     },c,0);
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
 private boolean discResolved(){try{return localDiscs!=null&&discResolved(currentSong());}catch(Exception unavailable){return false;}}
 private boolean discResolved(String song){return localDiscs!=null&&localDiscs.isResolved(song);}
 private void observeSources(){
  if(!running)return;
  try{
   String song=currentSong();if(localDiscs!=null)localDiscs.request(song);boolean changed=false;
   synchronized(frames){for(var it=frames.iterator();it.hasNext();){
    Frame frame=it.next().get();if(frame==null){it.remove();continue;}
    if(frame.secondary&&frame.nativeRoot!=null&&frame.nativeRoot.isAttached()){
     changed|=frame.observe(song);
     // Bootstrap only when enabled after the host's last completed read.
     // One host refresh per unknown track; never repeat or infer absence by timeout.
     if(coverStatus!=null&&frame.observedSource==null&&frame.ownedCover==null&&coverAvailability.refreshNeeded(song,System.nanoTime()))coverStatus.refreshCurrent();
    }
   }}
   if(changed){sourceRevision.setValue(sourceRevision.getValue()+1);report("SOURCE_REFRESH");}
  }catch(Exception error){report("SOURCE_OBSERVER_ERROR "+error.getClass().getSimpleName());}
 }
 private void animateFrames(){
  if(!running)return;
  long now=System.nanoTime();
  String song;try{song=currentSong();}catch(Exception unavailable){return;}
  if(localDiscs!=null)localDiscs.request(song);
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
   if(frame.nativeRoot==null||!frame.nativeRoot.isAttached()){frame.rotationAngle.setValue(frame.rotation.sample(song,rotate,false,false,now));continue;}
   try{
    frame.motion.setSpeed(display.motionSpeed());
    if(frame.motion.phase()==DiscTransition.Phase.IDLE){frame.motion.enter();frame.pose.setValue(frame.motion.pose());}
    sourceChanged|=frame.observe(song);
    boolean switched=frame.trackGate.observe(song,now);
    if(switched&&!frame.motion.closing()&&!frame.motion.finished()){
     frame.motion.trackChanged();frame.trackPending.setValue(true);frame.pose.setValue(frame.motion.pose());
     report("TRACK_ENTRY_PENDING");
    }
    Object source=frame.passPainter.getValue();
    if(frame.motion.phase()==DiscTransition.Phase.TRACK_WAIT){
     if(frame.trackGate.ready(song,source,frame.fallbackActive||frame.ownedCover!=null||HostCoverBridge.imageReady(source),discResolved(song),now)){
      frame.motion.trackReady(now);frame.trackPending.setValue(false);report("TRACK_ENTRY_START");
     }
    }else if(source!=null&&Boolean.TRUE.equals(frame.ready.getValue()))frame.motion.ready(now);
    frame.advanceMotion(now);
    boolean canRotate=rotate&&song!=null&&frame.motion.phase()==DiscTransition.Phase.OPEN
      &&(frame.fallbackActive||frame.ownedCover!=null)&&display.amount()>0&&discResolved(song);
    if(canRotate&&playing==null)playing=playbackState.playing(this::report);
    frame.rotationAngle.setValue(frame.rotation.sample(song,rotate,canRotate&&playing,canRotate,now,display.rotationSpeed().rotationPeriod));
   }catch(Exception error){frame.rotation.sample(song,rotate,false,false,now);frame.motion.reset();frame.pose.setValue(frame.motion.pose());report("ANIMATION_ERROR "+error.getClass().getSimpleName());}
  }}
  // The faster animation clock may see a new source before sourceObserver.
  // Publish that observation too, so a suppressed cached native layer cannot
  // leave the parent Canvas waiting for a change the observer already missed.
  if(sourceChanged)sourceRevision.setValue(sourceRevision.getValue()+1);
 }
 private String currentSong(){var item=WorkshopApi.playback().getCurrentMediaItem();return item==null?null:item.getPath();}
 private static long size(LayoutCoordinates c)throws Exception{return (Long)PainterDrawBinding.LAYOUT_SIZE.invoke(c);}
 @SuppressWarnings("unchecked") private void drawOriginal(List<Object> a,Modifier modifier,Composer c,Function0<Unit> toggle,boolean lyrics){
  host.drawOriginal(toggle,lyrics,(Boolean)a.get(2),(Boolean)a.get(3),modifier,c);
 }
 @Override public synchronized void stop(){
  enabled.setValue(false);running=false;coverAvailability.reset();coverStatus=null;
  if(animationClock!=null){animationClock.stop();animationClock=null;}
  if(discSettings!=null){try{discSettings.close();}catch(Exception error){report("SETTINGS_CLOSE_WARNING "+error.getClass().getSimpleName());}discSettings=null;}
  synchronized(frames){for(var ref:frames){Frame frame=ref.get();if(frame!=null)frame.restoreNative();}}
  if(sourceObserver!=null){sourceObserver.stop();sourceObserver=null;}
  if(localDiscs!=null){localDiscs.close();localDiscs=null;}
  for(int i=handles.size()-1;i>=0;i--)try{handles.get(i).close();}catch(Throwable e){report("UNHOOK_WARNING "+e.getClass().getSimpleName());}
  handles.clear();rawSource.remove();report("STOP SelfDraw disabled; native fallback");
 }
 @Override public void update(){stop();}@Override public void delete(){stop();}
 private void report(String message){
  StringBuilder safe=new StringBuilder();
  message.codePoints().limit(512).forEach(cp->safe.appendCodePoint(
   Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||cp==0x2028||cp==0x2029?' ':cp));
  byte[] line=(Instant.now()+" "+safe+System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  synchronized(logLock){
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
  }
 }
}
