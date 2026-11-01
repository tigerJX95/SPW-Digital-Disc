package io.github.tigerjx95.spwcdprobe;
import java.util.List;
public class CoverSelectionTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  CoverSelection c=new CoverSelection();Object a=new Object(),b=new Object(),d=new Object();long t=1_000_000_000L;
  c.song("A");check(c.select(List.of(a),a,t)==a,"initial cover");c.song("B");
  check(c.select(List.of(a,b),b,t)==b,"new song never carries old committed painter");
  check(c.select(List.of(a,b),a,t+60_000_000L)==b,"outgoing capture cannot replace new song");
  check(c.select(List.of(a,b),a,t+120_000_000L)==b,"settled incoming commits");
  for(int i=0;i<100;i++)check(c.select(i%2==0?List.of(a,b):List.of(b,a),i%2==0?a:b,t+130_000_000L+i*10_000_000L)==b,"old draw callbacks and area/order changes never revive outgoing cover");
  c.song("D");check(c.select(List.of(a,b,d),a,t+2_000_000_000L)==d,"historical attached nodes never commit for new song");
  check(c.select(List.of(d,b,a),b,t+2_120_000_000L)==d,"latest incoming commits despite old capture");
  check(c.select(List.of(a,d),a,t+3_000_000_000L)==d,"older-than-previous source cannot return");
  c.song("same album");check(c.select(List.of(d),d,t+4_000_000_000L)==null,"unchanged old painter alone cannot prove new-song ownership");
  check(c.select(List.of(),null,t+5_000_000_000L)==null,"detached catalog cannot return stale painter");
  System.out.println("PASS outgoing/incoming settle, 100 alternating capture/order frames without ping-pong, historical-node retirement, latest incoming, old-identity quarantine and detached source cleanup");
 }
}
