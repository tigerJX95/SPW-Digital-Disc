package io.github.tigerjx95.spwcdprobe;
import androidx.compose.runtime.*;
import androidx.compose.ui.Modifier;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import kotlin.Unit;
public class HostBindingsTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static class Renamed {
  public static long anotherName(int value){return value;}
  public static int unrelated(int value){return value;}
 }
 public static class Ambiguous {
  public static long first(int value){return value;}
  public static long second(int value){return value;}
 }
 static void checkRenamedClasses(HostCompatibility.Profile profile, ClassLoader loader)throws Exception{
  var archive=java.nio.file.Files.createTempFile("spw-renamed-classes", ".zip");
  try{
   try(var out=new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(archive))){
    var originals=java.util.List.of(profile.owner(),profile.caller(),profile.local());
    for(int i=0;i<originals.size();i++){
     String renamed="androidx/compose/ui/TestRenamed"+i;
     try(var in=loader.getResourceAsStream(originals.get(i).replace('.','/')+".class")){
      var reader=new net.bytebuddy.jar.asm.ClassReader(in);
      var writer=new net.bytebuddy.jar.asm.ClassWriter(0);
      reader.accept(new net.bytebuddy.jar.asm.ClassVisitor(net.bytebuddy.jar.asm.Opcodes.ASM9,writer){
       @Override public void visit(int version,int flags,String name,String signature,String parent,String[] interfaces){
        super.visit(version,flags,renamed,signature,parent,interfaces);
       }
      },0);
      out.putNextEntry(new java.util.zip.ZipEntry(renamed+".class"));
      out.write(writer.toByteArray());
      out.closeEntry();
     }
    }
   }
   var targets=HostClassDiscovery.discover(archive);
   check(targets.equals(new HostClassDiscovery.Targets("androidx.compose.ui.TestRenamed0",
    "androidx.compose.ui.TestRenamed1","androidx.compose.ui.TestRenamed2")),"metadata resolves renamed class entries");
  }finally{java.nio.file.Files.deleteIfExists(archive);}
 }
 public static void main(String[] args)throws Exception{
  check(HostBindings.uniqueStatic(Renamed.class,long.class,int.class).getName().equals("anotherName"),"signature resolves a renamed member");
  try{
   HostBindings.uniqueStatic(Ambiguous.class,long.class,int.class);
   throw new AssertionError("ambiguous signatures must be rejected");
  }catch(NoSuchMethodException expected){}
  var loader=HostBindingsTest.class.getClassLoader();var profile=HostCompatibility.current();var bindings=new HostBindings(profile,loader);
  checkRenamedClasses(profile,loader);
  check(bindings.caller.equals(profile.caller()),"discovered caller matches verified host");
  check(bindings.cover.getDeclaringClass().getName().equals(profile.owner()),"cover target uses detected profile");
  check(bindings.local.getDeclaringClass().getName().equals(profile.local()),"composition local uses same profile");
  var skips=new AtomicInteger();
  Composer composer=(Composer)Proxy.newProxyInstance(Composer.class.getClassLoader(),new Class[]{Composer.class},(p,m,a)->{
   if(m.getName().equals("startRestartGroup"))return p;
   if(m.getName().equals("skipToGroupEnd")){skips.incrementAndGet();return null;}
   if(m.getName().equals("getSkipping"))return true;
   Class<?> type=m.getReturnType();if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0f;return null;
  });
  bindings.drawOriginal(()->Unit.INSTANCE,false,false,false,Modifier.Companion,composer);
  check(skips.get()>0,"reflection invokes the actual host cover on a skipped composition pass");
  check(ProvidableCompositionLocal.class.isAssignableFrom(bindings.local.getReturnType()),"sheet local signature");
  check(bindings.sheetLocal()!=null&&bindings.sheetLocal()==bindings.sheetLocal(),"actual host composition-local getter returns its stable singleton");
  profile.verifyPlayback(loader);
  Class<?> service=Class.forName("com.xuncorp.voxzen.service.PlaybackService",false,loader);
  var getter=service.getMethod("getPlayer");
  check(getter.getDeclaringClass().getName().equals(profile.playback().get(1).name()),"getPlayer resolves the release-specific base class");
  check(getter.getReturnType().getMethod("ՠ").getReturnType()==boolean.class,"playing getter ABI");
  var shader=Class.forName(profile.painter(),false,loader).getDeclaredField("\u037f");check(shader.getType()==org.jetbrains.skia.Shader.class,"detected cover exposes the expected shader");
  System.out.println("PASS "+profile.version()+" actual cover invocation, mapped method signatures, composition-local binding, painter shader and playback-base resolution (native pixels/audio excluded)");
 }
}
