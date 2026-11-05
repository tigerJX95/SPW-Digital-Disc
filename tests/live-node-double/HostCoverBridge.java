package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.layout.LayoutCoordinates;
import java.util.*;
// Test-only native node catalog. Never included in the plugin.
final class HostCoverBridge {
 static final Set<Object> loading=Collections.newSetFromMap(new IdentityHashMap<>());
 static boolean imageReady(Object painter){return painter!=null&&!loading.contains(painter);}
 record Image(Object painter,LayoutCoordinates coordinates,long area){}
 static final Map<LayoutCoordinates,List<Image>> nodes=new IdentityHashMap<>();
 static boolean anyPainter(LayoutCoordinates root,Object painter,java.util.function.Predicate<LayoutCoordinates> matches){
  return images(root).stream().anyMatch(image->image.painter()==painter&&matches.test(image.coordinates()));
 }
 static List<Image> images(LayoutCoordinates root){return nodes.getOrDefault(root,List.of());}
 static LayoutCoordinates lastInvalidated;
 static void invalidate(LayoutCoordinates root){lastInvalidated=root;}
}
