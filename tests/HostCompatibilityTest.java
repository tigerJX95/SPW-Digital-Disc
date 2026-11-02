package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.*;
import java.io.*;
public class HostCompatibilityTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static PluginContext context(String version,Channel channel){return new PluginContext("io.github.tigerjx95.spw.digitaldisc","1.1.1","plugin.spmod",version,channel);}
 static final ClassLoader REAL=HostCompatibilityTest.class.getClassLoader();
 static final String[] PATHS=HostCompatibility.current().critical().stream().map(e->e.name().replace('.','/')+".class").toArray(String[]::new);
 static ClassLoader changed(String target,boolean missing){return new ClassLoader(REAL){
  @Override public InputStream getResourceAsStream(String name){
   if(!name.equals(target))return super.getResourceAsStream(name);
   if(missing)return null;
   try(var in=super.getResourceAsStream(name)){byte[] bytes=in.readAllBytes();bytes[bytes.length-1]^=1;return new ByteArrayInputStream(bytes);}catch(IOException e){throw new UncheckedIOException(e);}
  }
 };}
 public static void main(String[] args){
  String os=System.getProperty("os.name");int accepted=0,rejected=0;
  try{
   for(String platform:new String[]{"Windows 10","Windows 11"}){
    System.setProperty("os.name",platform);
    for(Channel channel:Channel.values())for(String version:new String[]{"1.19.0","1.19.0.0","1.19.0 (Steam)"}){
     // These are metadata variants over the SAME known host classes, not a
     // claim of real-machine validation against another channel/build.
     HostCompatibility.verify(context(version,channel),REAL);accepted++;
    }
   }
  }finally{if(os==null)System.clearProperty("os.name");else System.setProperty("os.name",os);}
  for(String path:PATHS)for(boolean missing:new boolean[]{false,true}){
   try{HostCompatibility.verify(context(HostCompatibility.current().version()+"-reported-build",Channel.Steam),changed(path,missing));throw new AssertionError("unrecognized class must be rejected");}
   catch(IllegalStateException expected){String message=expected.getMessage();check(message.contains("reported-build")&&message.contains("Steam")&&message.contains(path.replace('/','.').replace(".class","")),"diagnostic identifies actual build/channel/class");check(message.contains("ffmpeg-x64.dll"),"diagnostic explains required adaptation input");rejected++;}
  }
  System.out.println("PASS host preflight: "+accepted+" OS/channel/version metadata combinations over known real class bytes; "+rejected+" changed/missing critical classes rejected with actionable diagnostics. Each JVM run uses its actual supplied host archive.");
 }
}
