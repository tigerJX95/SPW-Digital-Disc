package io.github.tigerjx95.spwcdprobe;
import java.lang.reflect.*;
import org.jetbrains.skia.Bitmap;
import com.xuncorp.spw.workshop.api.*;
/** Requires a Skiko native test backend; does not launch the SPW application. */
public class OwnedCoverTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 public static void main(String[] args)throws Exception{
  Bitmap bitmap=new Bitmap();check(bitmap.allocN32Pixels(512,256,false),"allocate host bitmap");bitmap.erase(0xffc42637);
  var painterType=Class.forName(HostCompatibility.current().painter());var pctor=painterType.getDeclaredConstructor(Bitmap.class);pctor.setAccessible(true);
  Object painter=pctor.newInstance(bitmap);check(HostCoverBridge.imageReady(painter),"actual host mipmap painter ready");
  var plugin=new CdPageProbePlugin(new PluginContext("test","1.0.1",".","1.19.0",Channel.MS));
  var type=Class.forName(CdPageProbePlugin.class.getName()+"$Frame");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);Object frame=ctor.newInstance(plugin);
  field(type,"observedSong").set(frame,"song A");
  var resolve=type.getDeclaredMethod("resolveCover",Object.class);resolve.setAccessible(true);
  check(resolve.invoke(frame,painter)==painter,"handoff captures independent pixels");
  var owned=(DiscImage)field(type,"ownedCover").get(frame);check(owned!=null,"snapshot created");
  Bitmap copy=(Bitmap)field(DiscImage.class,"nativeBitmap").get(owned);
  check(copy.getWidth()==512&&copy.getHeight()==256,"source aspect and full dimensions preserved");
  ((AutoCloseable)painter).close();bitmap.close();check(!HostCoverBridge.imageReady(painter),"closed native shader cannot be accepted again");
  for(int i=0;i<100;i++){
   check(resolve.invoke(frame,new Object[]{null})==painter,"native node disappearance cannot revoke current song pixels");
   check(copy.getColor(100,100)==0xffc42637,"independent pixels survive native painter and bitmap disposal");
  }
  var observe=type.getDeclaredMethod("observe",String.class);observe.setAccessible(true);observe.invoke(frame,"song B");
  check(field(type,"ownedCover").get(frame)==null&&field(type,"pendingCover").get(frame)==owned&&!copy.isClosed(),"track change moves snapshot to visual-only waiting state");
  check(resolve.invoke(frame,new Object[]{null})==owned&&!HostCoverBridge.imageReady(owned),"waiting pixels are not decoded new-track readiness");
  var clear=type.getDeclaredMethod("clearCover");clear.setAccessible(true);clear.invoke(frame);
  check(copy.isClosed()&&resolve.invoke(frame,new Object[]{null})==null,"hide/dispose releases waiting pixels");
  System.out.println("PASS actual host mipmap painter capture, native shader/bitmap disposal, 100 detached-node handoffs with intact pixels, original aspect, visual-only track wait, no early readiness and explicit cleanup");
 }
}
