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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Replaces the cover DISPLAY layer with a separately measured sibling Canvas. */
public final class CdPageProbePlugin extends SpwPlugin {
 private static final String OWNER="androidx.compose.ui.א",METHOD="\u037f";
 private final List<HookHandle> handles=new ArrayList<>();
 private final List<WeakReference<Frame>> frames=new ArrayList<>();
 private final MutableState<Boolean> enabled=state(false);
 private final ThreadLocal<Boolean> rawSource=ThreadLocal.withInitial(()->false);
 private final Path log;
 private volatile boolean running;
 private javax.swing.Timer sourceObserver,animationClock;
 private final MutableState<Long> sourceRevision=state(0L);
 private final MutableState<Long> discRevision=state(0L);
 private LocalDiscService<DiscImage> localDiscs;
 private record Viewport(int width,int height){}
 private final class Frame {
  final MutableState<Viewport> viewport=state(new Viewport(0,0));
  final MutableState<Object> artwork=state(null);
  final MutableState<Object> rejectedSource=state(null);
  final MutableState<Boolean> ready=state(false);
  volatile LayoutCoordinates nativeRoot;
  PlayerSheetState sheet;
  boolean renderAllowed=true,failed,suppressionApplied,secondary;
  Object lastPainted;
  final MutableState<Object> passPainter=state(null);
  Object observedSource;
  boolean observedReady;
  String observedSong;
  ReflectionRenderer reflection;
  final MutableState<Boolean> retainingReturn=state(false);
  final RevealCycle revealCycle=new RevealCycle();
  final CoverSelection coverSelection=new CoverSelection();
  final TrackEntryGate trackGate=new TrackEntryGate();
  final MutableState<Boolean> trackPending=state(false);
  final DiscTransition motion=new DiscTransition();
  final MutableState<DiscTransition.Pose> pose=state(motion.pose());
  String lastBoundsError="";
  boolean suppress(){
   discRevision.getValue(); // Resolve completion must redraw even while Canvas is waiting.
   sourceRevision.getValue(); // Native subtree/cached image completion is not always a parent recomposition.
   return secondary&&Boolean.TRUE.equals(enabled.getValue())&&Boolean.TRUE.equals(ready.getValue())&&!failed&&renderAllowed&&sheet!=null&&sheet.Ԩ()&&sheet.ԩ()&&HostCoverBridge.imageReady(source())&&(Boolean.TRUE.equals(trackPending.getValue())||discResolved());
  }
  void restoreNative(){
   secondary=false;ready.setValue(false);revealCycle.reset();motion.reset();trackGate.reset();trackPending.setValue(false);retainingReturn.setValue(false);pose.setValue(motion.pose());
   if(suppressionApplied)HostCoverBridge.invalidate(nativeRoot);
   passPainter.setValue(null);suppressionApplied=false;
  }
  void syncVisibility(boolean visible){
   RevealCycle.Edge edge=revealCycle.sample(visible);
   if(edge==RevealCycle.Edge.NONE)return;
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
   float w=RootRenderer.x(drawSize),h=RootRenderer.y(drawSize);
   for(var image:images())if(image.painter()==painter)try{
    long measured=size(image.coordinates());
    if(Math.abs((int)(measured>>>32)-w)<=Math.max(6,w*.05f)&&Math.abs((int)measured-h)<=Math.max(6,h*.05f))return true;
   }catch(Exception ignored){}
   return false;
  }
  Object source(){
   Object captured=artwork.getValue(),rejected=rejectedSource.getValue();
   var current=images();
   List<Object> decoded=new ArrayList<>();
   for(var image:current)if(image.painter()!=rejected&&HostCoverBridge.imageReady(image.painter()))decoded.add(image.painter());
   return coverSelection.select(decoded,captured,System.nanoTime());
  }

  boolean observe(String song){
   coverSelection.song(song);
   boolean changed=!Objects.equals(song,observedSong);
   if(changed){
    observedSong=song;failed=false;rejectedSource.setValue(null);artwork.setValue(null);
    passPainter.setValue(null);lastPainted=null;HostCoverBridge.invalidate(nativeRoot);
   }
   Object current=source();boolean available=HostCoverBridge.imageReady(current);
   if(changed||current!=observedSource||available!=observedReady){
    observedSource=current;observedReady=available;
    report("SOURCE_STATE song="+song+" painter="+(current==null?"none":current.getClass().getName())+" decoded="+available+" failed="+failed);
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
  if(!"1.19.0".equals(getPluginContext().getSpwVersion())||getPluginContext().getSpwChannel()!=Channel.MS)throw new IllegalStateException("Requires SPW 1.19.0 MS");
  if(!WorkshopApi.manager().isPermissionGranted(PluginPermission.CLASS_TRANSFORM)||!WorkshopApi.manager().isPermissionGranted(PluginPermission.LIBRARY_READ))throw new IllegalStateException("Grant class-transform and library-read permissions");
  try{
   fingerprint(OWNER,"e226ee353de834409b93abeaffebe23a7d197749b3d19afc69fd1dfab84f704e");
   fingerprint("androidx.compose.ui.ח","9a683e5cac898bf086cf58ee0e70ede6ba7e4fa4a7b5cc31134336e31b0eda05");
   handles.add(WorkshopApi.hookRegistrar().replaceComposable(OWNER,METHOD,"androidx.compose.ui.ח",
    List.of("kotlin.jvm.functions.Function0","boolean","boolean","boolean","androidx.compose.ui.Modifier"),50,new ComposableHook(){
     @Override public void Content(UiHookCall call,Composer composer,int changed){content(call,composer);}
    }));
   handles.add(WorkshopApi.hookRegistrar().hookMethod("androidx.compose.ui.graphics.painter.Painter","draw-x_KDEd0",
    List.of("androidx.compose.ui.graphics.drawscope.DrawScope","long","float","androidx.compose.ui.graphics.ColorFilter"),new MethodHook(50){
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
   report("START P4.10.2 Presentation Resume; settled visibility edge; fresh handoff; latest track entry; hooks="+handles.size());
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
  frame.sheet=(PlayerSheetState)composer.consume(androidx.compose.ui.ѐ.Ϳ());
  var args=call.getArguments();frame.renderAllowed=(Boolean)args.get(3);
  boolean expanded=frame.sheet.Ԩ()&&frame.sheet.ԩ();boolean lyrics=(Boolean)args.get(1);
  if(!lyrics&&!frame.secondary){frame.trackGate.reset();frame.motion.enter();frame.pose.setValue(frame.motion.pose());}
  // Native return paths may change the page before invoking our cover callback.
  // Retain the secondary visual until its disc is fully retracted.
  if(lyrics&&frame.secondary&&!frame.motion.finished()){
   frame.retainingReturn.setValue(true);
   frame.motion.close(System.nanoTime(),()->frame.retainingReturn.setValue(false));frame.pose.setValue(frame.motion.pose());
  }
  boolean visualLyrics=lyrics&&!frame.retainingReturn.getValue();
  if(visualLyrics)frame.restoreNative();
  frame.secondary=!visualLyrics;
  Function0<Unit> nativeToggle=(Function0<Unit>)args.get(0);
  Function0<Unit> animatedToggle=()->{
   if(frame.secondary&&(frame.motion.closing()||frame.motion.finished()))return Unit.INSTANCE;
   if(!frame.secondary||!frame.suppressionApplied){nativeToggle.invoke();return Unit.INSTANCE;}
   frame.motion.close(System.nanoTime(),()->{
    frame.pose.setValue(frame.motion.pose());nativeToggle.invoke();
   });
   frame.pose.setValue(frame.motion.pose());return Unit.INSTANCE;
  };
  boolean active=!visualLyrics&&Boolean.TRUE.equals(enabled.getValue())&&expanded&&frame.renderAllowed;
  frame.syncVisibility(active);
  Viewport viewport=frame.viewport.getValue();
  SelfDrawLayout plan=SelfDrawLayout.fit(viewport.width(),viewport.height(),density.getDensity(),!visualLyrics);
  if(!active||!plan.usable(density.getDensity()))frame.ready.setValue(false);
  Modifier box=SizeKt.fillMaxSize((Modifier)args.get(4),1f);
  box=OnGloballyPositionedModifierKt.onGloballyPositioned(box,c->{
   try{long size=size(c);frame.viewport.setValue(new Viewport((int)(size>>>32),(int)size));}catch(Exception e){report("VIEWPORT_ERROR "+e);}
   return Unit.INSTANCE;
  });
  box=DrawModifierKt.drawWithContent(box,scope->{
   // Observes both enabled and ready. Turning the new widget on/off refreshes
   // cached native image layers so the fallback never stays blank/duplicated.
   frame.passPainter.setValue(frame.suppress()?frame.source():null);
   boolean suppression=frame.passPainter.getValue()!=null;
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
      Object painter=frame.passPainter.getValue();
      if(!frame.secondary||painter==null)return Unit.INSTANCE;
      try{
       discRevision.getValue(); // Observe async image publication, including same-song file replacement.
       RootRenderer.Disc disc=(target,cx,cy,diameter)->{
        LocalDiscService<DiscImage> service=localDiscs;
        String song;
        try{song=currentSong();}catch(Exception unavailable){return true;}
        // true also means pending: draw cover only, never a temporary silver CD.
        try{return service==null||service.drawResolved(song,image->image.draw(target,cx,cy,diameter));}
        catch(Exception error){if(service!=null)service.discard(song);report("LOCAL_CD_DRAW_ERROR "+error+"; cause="+error.getCause());return true;}
       };
       var canvas=scope.getDrawContext().getCanvas();canvas.save();
       try{
        float offset=plan.side()*.25f*motion.centered();
        float pivot=plan.side()*(.75f-.25f*motion.centered());
        canvas.translate(offset+pivot,plan.side()*.5f);
        canvas.scale(motion.scale(),motion.scale());
        canvas.translate(-pivot,-plan.side()*.5f);
        RootRenderer.draw(scope,painter,()->rawSource.set(true),()->rawSource.remove(),disc,motion.exposed());
        float gap=4*density.getDensity(),reflectedHeight=plan.height()-plan.side()-gap;
        if(reflectedHeight>0){
         if(frame.reflection==null)frame.reflection=new ReflectionRenderer();
         try{frame.reflection.draw(scope,plan.side(),plan.width(),reflectedHeight,gap,()->RootRenderer.draw(scope,painter,()->rawSource.set(true),()->rawSource.remove(),disc,motion.exposed()));}
         catch(Exception error){report("REFLECTION_ERROR "+error+"; cause="+error.getCause());}
        }
       }finally{canvas.restore();}
       if(frame.lastPainted!=painter){frame.lastPainted=painter;report("SELF_DRAW source="+painter.getClass().getName()+" widgetPx="+plan.width()+"x"+plan.height()+" coverSidePx="+plan.side()+" secondary="+plan.secondary());}
      }catch(Throwable error){frame.passPainter.setValue(null);frame.rejectedSource.setValue(painter);HostCoverBridge.invalidate(frame.nativeRoot);report("SELF_DRAW_SOURCE_ERROR native fallback until a fresh source: "+error);}
      return Unit.INSTANCE;
     },c,0);
    }catch(Exception error){frame.failed=true;frame.ready.setValue(false);report("WIDGET_ERROR "+error);}
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
 private boolean discResolved(){try{return localDiscs!=null&&localDiscs.isResolved(currentSong());}catch(Exception unavailable){return false;}}
 private void observeSources(){
  if(!running)return;
  try{
   String song=currentSong();boolean changed=false;
   synchronized(frames){for(var it=frames.iterator();it.hasNext();){
    Frame frame=it.next().get();if(frame==null){it.remove();continue;}
    if(frame.secondary&&frame.nativeRoot!=null&&frame.nativeRoot.isAttached())changed|=frame.observe(song);
   }}
   if(changed){sourceRevision.setValue(sourceRevision.getValue()+1);report("SOURCE_REFRESH song="+song);}
  }catch(Exception error){report("SOURCE_OBSERVER_ERROR "+error);}
 }
 private void animateFrames(){
  if(!running)return;
  long now=System.nanoTime();
  String song;try{song=currentSong();}catch(Exception unavailable){return;}
  synchronized(frames){for(var it=frames.iterator();it.hasNext();){
   Frame frame=it.next().get();if(frame==null){it.remove();continue;}
   // Expanded target alone is insufficient: animation must not run while the
   // host is still moving from/to the mini player (including interrupted gestures).
   boolean visible=frame.secondary&&frame.renderAllowed&&frame.nativeRoot!=null&&frame.nativeRoot.isAttached()
     &&frame.sheet!=null&&frame.sheet.Ԩ()&&frame.sheet.ԩ();
   frame.syncVisibility(visible);
   if(!visible)continue;
   try{
    if(frame.motion.phase()==DiscTransition.Phase.IDLE){frame.motion.enter();frame.pose.setValue(frame.motion.pose());}
    frame.coverSelection.song(song);
    boolean switched=frame.trackGate.observe(song,now);
    if(switched&&!frame.motion.closing()&&!frame.motion.finished()){
     frame.motion.trackChanged();frame.trackPending.setValue(true);frame.pose.setValue(frame.motion.pose());
     report("TRACK_ENTRY_PENDING song="+song);
    }
    Object source=frame.passPainter.getValue();
    if(frame.motion.phase()==DiscTransition.Phase.TRACK_WAIT){
     if(frame.trackGate.ready(song,source,HostCoverBridge.imageReady(source),discResolved(),now)){
      frame.motion.trackReady(now);frame.trackPending.setValue(false);report("TRACK_ENTRY_START song="+song);
     }
    }else if(source!=null&&Boolean.TRUE.equals(frame.ready.getValue()))frame.motion.ready(now);
    if(frame.motion.tick(now))frame.pose.setValue(frame.motion.pose());
   }catch(Exception error){frame.motion.reset();frame.pose.setValue(frame.motion.pose());report("ANIMATION_ERROR "+error);}
  }}
 }
 private String currentSong(){var item=WorkshopApi.playback().getCurrentMediaItem();return item==null?null:item.getPath();}
 private static long size(LayoutCoordinates c)throws Exception{return (Long)LayoutCoordinates.class.getMethod("getSize-YbymL2g").invoke(c);}
 @SuppressWarnings("unchecked") private static void drawOriginal(List<Object> a,Modifier modifier,Composer c,Function0<Unit> toggle,boolean lyrics){
  androidx.compose.ui.א.Ϳ(toggle,lyrics,(Boolean)a.get(2),(Boolean)a.get(3),modifier,c,0,0);
 }
 @Override public synchronized void stop(){
  enabled.setValue(false);running=false;
  if(animationClock!=null){animationClock.stop();animationClock=null;}
  synchronized(frames){for(var ref:frames){Frame frame=ref.get();if(frame!=null)frame.restoreNative();}}
  if(sourceObserver!=null){sourceObserver.stop();sourceObserver=null;}
  if(localDiscs!=null){localDiscs.close();localDiscs=null;}
  for(int i=handles.size()-1;i>=0;i--)try{handles.get(i).close();}catch(Throwable e){report("UNHOOK_WARNING "+e);}
  handles.clear();rawSource.remove();report("STOP SelfDraw disabled; native fallback");
 }
 @Override public void update(){stop();}@Override public void delete(){stop();}
 private void fingerprint(String owner,String expected)throws Exception{
  try(var in=getClass().getClassLoader().getResourceAsStream(owner.replace('.','/')+".class")){
   if(in==null||!expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))throw new IllegalStateException("Host fingerprint mismatch: "+owner);
  }
 }
 private synchronized void report(String s){
  try{Files.createDirectories(log.toAbsolutePath().getParent());Files.writeString(log,Instant.now()+" "+s+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){System.err.println(s);}
 }
}
