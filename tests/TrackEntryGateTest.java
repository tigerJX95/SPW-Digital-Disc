package io.github.tigerjx95.spwcdprobe;
import java.util.concurrent.atomic.AtomicInteger;
public class TrackEntryGateTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  long t=1_000_000_000L;Object art=new Object();TrackEntryGate gate=new TrackEntryGate();DiscTransition motion=new DiscTransition();
  check(!gate.observe("A",t),"initial page binds without extra track replay");
  motion.enter();motion.ready(t);motion.tick(t+940_000_000);check(motion.pose().exposed()==1,"page entry settles");
  int entries=0;String song="A";
  // Ten rapid changes, every 80 ms. None may start until the final selection settles.
  for(int i=0;i<10;i++){
   song="song"+i;long now=t+2_000_000_000L+i*80_000_000L;
   check(gate.observe(song,now),"new request replaces pending song");motion.trackChanged();
   check(motion.pose().exposed()==0&&motion.pose().scale()==1&&motion.pose().centered()==0,"hide disc without moving/rescaling cover");
   check(!gate.ready(song,art,true,true,now),"no immediate eject");
   check(!gate.ready(song,art,true,true,now+79_000_000L),"rapid burst stays coalesced");
   check(!gate.ready("oldSong",art,true,true,now+500_000_000L),"stale readiness cannot consume latest request");
  }
  long last=t+2_000_000_000L+9*80_000_000L;
  check(!gate.ready(song,art,true,true,last+279_000_000L),"quiet boundary respected");
  if(gate.ready(song,art,true,true,last+280_000_000L)){entries++;motion.trackReady(last+280_000_000L);}
  check(entries==1,"one eject for ten rapid selections");
  check(!gate.ready(song,art,true,true,last+900_000_000L),"same pending request fires once");
  motion.tick(last+630_000_000L);check(Math.abs(motion.pose().exposed()-.5f)<.001&&motion.pose().centered()==0,"track entry eases with stationary cover");
  motion.tick(last+980_000_000L);check(motion.pose().exposed()==1,"last disc settles");
  // Separate, ready track changes all receive their own one-way eject.
  for(int i=0;i<3;i++){
   long now=last+2_000_000_000L+i*2_000_000_000L;song="isolated"+i;
   check(gate.observe(song,now),"spaced selection observed");motion.trackChanged();gate.ready(song,art,true,true,now);
   if(gate.ready(song,art,true,true,now+280_000_000L)){entries++;motion.trackReady(now+280_000_000L);}
   motion.tick(now+980_000_000L);check(motion.pose().exposed()==1,"each isolated track animates");
  }
  check(entries==4,"three spaced changes not swallowed");
  long now=last+10_000_000_000L;gate.observe("slow",now);motion.trackChanged();
  check(!gate.ready("slow",art,true,false,now+2_000_000_000L),"decode must finish");
  check(!gate.ready("slow",art,false,true,now+3_000_000_000L),"cover must finish");
  check(!gate.ready("slow",art,true,true,now+4_000_000_000L),"ready settle starts after decode");
  Object replacement=new Object();check(!gate.ready("slow",replacement,true,true,now+4_110_000_000L),"changing native source restarts readiness settle");
  check(!gate.ready("slow",replacement,true,true,now+4_229_000_000L),"new source still settling");
  check(gate.ready("slow",replacement,true,true,now+4_230_000_000L),"only stable ready source starts");
  motion.trackReady(now+4_230_000_000L);motion.tick(now+4_580_000_000L);AtomicInteger back=new AtomicInteger();motion.close(now+4_580_000_000L,back::incrementAndGet);
  float closing=motion.pose().exposed();motion.trackChanged();check(motion.closing()&&motion.pose().exposed()==closing,"track change cannot cancel pending return");
  motion.tick(now+6_000_000_000L);check(back.get()==1,"return completes once");
  gate.observe("cancel",now);gate.reset();check(!gate.ready("cancel",art,true,true,now+9_000_000_000L),"unload clears pending request");
  gate.reset();gate.observe("A",now);
  gate.request("B",now+1);gate.request("A",now+2);
  check(!gate.ready("A",art,true,true,now+2),"draw-time ABA starts settle");
  check(gate.ready("A",art,true,true,now+280_000_002L),"draw-time requests cannot strand TRACK_WAIT between timer ticks");
  System.out.println("PASS latest-only ten-track burst, 280ms quiet interval, one-shot request, three spaced-track ejects, slow decode, stable ready source, fixed cover geometry, stale-song rejection, return protection and reset cancellation");
 }
}
