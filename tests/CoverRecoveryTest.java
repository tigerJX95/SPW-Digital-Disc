package io.github.tigerjx95.spwcdprobe;
import java.util.List;
/** Reproduces a temporary native painter disappearing after the CD is open. */
public class CoverRecoveryTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  CoverSelection selection=new CoverSelection();
  Object original=new Object(),temporary=new Object(),next=new Object();long t=1_000_000_000L;
  selection.song("song A");
  check(selection.select(List.of(original),original,t)==original,"original live cover");
  selection.select(List.of(original,temporary),temporary,t+10_000_000L);
  check(selection.select(List.of(original,temporary),temporary,t+140_000_000L)==temporary,"temporary node takes over after settling");
  check(selection.select(List.of(original),original,t+150_000_000L)==original,"sole same-song original must recover after temporary node detaches");
  for(int i=0;i<100;i++)check(selection.select(List.of(original),original,t+160_000_000L+i*16_000_000L)==original,"recovered source remains available");
  selection.song("song B");
  check(selection.select(List.of(temporary),temporary,t+2_000_000_000L)==null,"recovery cannot revive an older song's temporary source");
  check(selection.select(List.of(next),next,t+2_100_000_000L)==next,"new song can recover normally");
  check(selection.select(List.of(),null,t+2_200_000_000L)==null,"detached sources are never drawn");
  System.out.println("PASS temporary-painter removal recovery, 100 stable frames, previous-song exclusion and empty-node fallback");
 }
}
