package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.layout.LayoutCoordinates;
import java.util.*;
/** Supplies live host-source events without a native Skiko backend. */
final class HostCoverBridge {
 record Decoded(DiscImage pixels){}
 record Image(Object painter,LayoutCoordinates coordinates,long area){}
 static List<Image> nodes=List.of();
 static List<Image> images(LayoutCoordinates root){return nodes;}
 static boolean imageReady(Object painter){return painter instanceof Decoded;}
 static DiscImage capture(Object painter){return ((Decoded)painter).pixels();}
 static void invalidate(LayoutCoordinates root){}
}
