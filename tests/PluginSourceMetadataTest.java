package io.github.tigerjx95.spwcdprobe;
import java.nio.file.*;
import java.util.jar.Manifest;
public class PluginSourceMetadataTest {
 public static void main(String[] args)throws Exception{
  try(var in=Files.newInputStream(Path.of("plugin/MANIFEST.MF"))){
   var attributes=new Manifest(in).getMainAttributes();
   String url=attributes.getValue("Plugin-Open-Source-Url");
   if(!"https://github.com/tigerJX95/SPW-Digital-Disc".equals(url))throw new AssertionError("host-supported open-source metadata key/value");
   if(!"class-transform,library-read".equals(attributes.getValue("Plugin-Permissions")))throw new AssertionError("existing permissions retained");
   if(!"io.github.tigerjx95.spw.digitaldisc".equals(attributes.getValue("Plugin-Id")))throw new AssertionError("existing plugin identity retained");
  }
  System.out.println("PASS parsed Manifest open-source URL, unchanged permissions and plugin identity");
 }
}
