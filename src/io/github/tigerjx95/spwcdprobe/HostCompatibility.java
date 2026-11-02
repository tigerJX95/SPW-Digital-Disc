package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.PluginContext;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
/** Complete, pinned mappings: never mix individual classes from two releases. */
final class HostCompatibility {
 record Entry(String name,String sha256){}
 record Profile(String version,String owner,String caller,String painter,String local,List<Entry> critical,List<Entry> playback){
  void verifyPlayback(ClassLoader loader){String error=mismatch(playback,loader);if(error!=null)throw new IllegalStateException("Playback "+version+": "+error);}
 }
 private static final List<Profile> PROFILES=List.of(
  new Profile("1.19.0","androidx.compose.ui.א","androidx.compose.ui.ח","androidx.compose.ui.ƙ","androidx.compose.ui.ѐ",
   List.of(new Entry("androidx.compose.ui.א","e226ee353de834409b93abeaffebe23a7d197749b3d19afc69fd1dfab84f704e"),
    new Entry("androidx.compose.ui.ח","9a683e5cac898bf086cf58ee0e70ede6ba7e4fa4a7b5cc31134336e31b0eda05"),
    new Entry("androidx.compose.ui.ƙ","d9542ceeaf69f14205b5684286f2cf2ccab797b02ef205af6e7781f7470af78a"),
    new Entry("androidx.compose.ui.ѐ","0bf067c62320fa443cea95f60ae17f5c65acf322a964b19ac46eb69bfc4f742d"),
    new Entry("com.xuncorp.voxzen.ui.component.PlayerSheetState","37d767f12b2d716856a4ca093e72ffa1e2ef9304566e608b35b4696f37e86d80")),
   List.of(new Entry("com.xuncorp.voxzen.service.PlaybackController","9d6515f6886ca7fdd74d9dbe3f80294e5e537e5c92ae9457a134cc4d6aba6907"),
    new Entry("androidx.compose.ui.ݱ","c5d71ffba956640547e0a63bf9287ef1b491c978df0c0f1e1c5759cadeb11b36"),
    new Entry("com.xuncorp.pisces.PiscesPlayer","66cacfe8b14d395263c8747a6d579af3441b857ca80e396181200c3ae15635f8"))),
  new Profile("1.19.3","androidx.compose.ui.ଦ","androidx.compose.ui.ڠ","androidx.compose.ui.Ƒ","androidx.compose.ui.К",
   List.of(new Entry("androidx.compose.ui.ଦ","c830630f48e4eb587db1b0f92c197d2a89d59c8f06abe73a570b2512704d3597"),
    new Entry("androidx.compose.ui.ڠ","da82af315aea80587d680661c32ade749abe7097790232106940a7d3d71df591"),
    new Entry("androidx.compose.ui.Ƒ","14becdacb52638190153a76b83e548c8913da07a34e18f178a9fb3c5f38d93a1"),
    new Entry("androidx.compose.ui.К","e3a3e6a6f69a5001eff38b7d1bd919c7202e8a91cc2c6b3fd3d93f0b4bc145ac"),
    new Entry("com.xuncorp.voxzen.ui.component.PlayerSheetState","37d767f12b2d716856a4ca093e72ffa1e2ef9304566e608b35b4696f37e86d80")),
   List.of(new Entry("com.xuncorp.voxzen.service.PlaybackController","753ff00802ef1bfe5885f33c7fd6b7e3fd59f3d520cb80fc94fde319e862559a"),
    new Entry("androidx.compose.ui.મ","c69a5d5ac155b93e56b7308878ffc692242f6abf55f5ba65b2a0883f0b1cc1a9"),
    new Entry("com.xuncorp.pisces.PiscesPlayer","2ce46088303f535dc32f4f5fdf3a4f970761d26be9d89966d2bc30b89f82dcbc")))
 );
 static Profile verify(PluginContext context,ClassLoader loader){return resolve("SPW "+context.getSpwVersion()+" / "+context.getSpwChannel(),context.getSpwVersion(),loader);}
 private static Profile resolve(String actual,String hint,ClassLoader loader){
  String failure="";
  for(Profile profile:PROFILES){
   String error=mismatch(profile.critical(),loader);
   if(error==null)return profile;
   if(failure.isEmpty()||hint.startsWith(profile.version()))failure=profile.version()+": "+error;
  }
  throw new IllegalStateException("不兼容的宿主接口："+actual+"；"+failure+"。请提供当前安装目录 app/ffmpeg-x64.dll 以核对适配。");
 }
 private static String mismatch(List<Entry> entries,ClassLoader loader){
  for(Entry entry:entries)try(InputStream in=loader.getResourceAsStream(entry.name().replace('.','/')+".class")){
   if(in==null)return entry.name()+" (missing class)";
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
   if(!entry.sha256().equals(hash))return entry.name()+" (class fingerprint differs)";
  }catch(Exception e){return entry.name()+" ("+e+")";}
  return null;
 }
 private static final class Detected {static final Profile VALUE=resolve("当前宿主","",HostCompatibility.class.getClassLoader());}
 static Profile current(){return Detected.VALUE;}
}
