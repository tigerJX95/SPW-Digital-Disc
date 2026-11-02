package io.github.tigerjx95.spwcdprobe;
final class TestHost {
 static boolean newer(){return HostCompatibility.current().version().equals("1.19.3");}
 static String helper(){return "androidx.compose.ui."+(newer()?"ე":"Ƌ");}
 static String parser(){return "androidx.compose.ui."+(newer()?"ӣ":"ҽ");}
 static String model(){return "androidx.compose.ui."+(newer()?"ݻ":"Ⴆ");}
}
