package io.github.tigerjx95.spwcdprobe;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
public class TrackEntryGateTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
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
  cueSegmentChanges();
  System.out.println("PASS latest-only track entry, stable artwork, return protection and CUE segment/cross-disc transitions");
 }

 private static void cueSegmentChanges()throws Exception{
  String discOne="专辑/第1碟.wav",discTwo="专辑/第2碟.wav";
  String first=cueState(discOne,0),second=cueState(discOne,750),third=cueState(discOne,1500);
  CuePlaybackTrack cue=new CuePlaybackTrack();
  TrackEntryGate gate=new TrackEntryGate();
  Object cover=new Object();
  long now=100_000L;
  check(!cue.changed(discOne,null)&&!gate.observe(discOne,now),"page entry with CUE discovery pending");
  check(!cue.changed(discOne,first),"first CUE advertisement only establishes playback identity");
  check(!cue.changed(discOne,first),"unchanged during pause, resume and seek within the first segment");
  for(String next:new String[]{second,third,second,third}){
   now+=2_000_000_000L;
   check(cue.changed(discOne,next),"natural advance, direct selection and previous/next change the segment");
   check(!gate.observe(discOne,now),"physical audio remains unchanged");
   gate.request(discOne,now);
   check(!gate.ready(discOne,cover,true,true,now),"use the existing selection settle period");
   check(gate.ready(discOne,cover,true,true,now+TrackEntryGate.QUIET_NS),"same cover/artwork can animate the CUE track");
   check(!gate.ready(discOne,cover,true,true,now+TrackEntryGate.QUIET_NS+1),"one eject per settled CUE segment");
   check(!cue.changed(discOne,next)&&!cue.changed(discOne,null)&&!cue.changed(discOne,next),
       "a repeated segment or temporary CUE refresh cannot replay the animation");
  }
  now+=2_000_000_000L;
  check(gate.observe(discTwo,now)&&!cue.changed(discTwo,third),"cross-disc uses the physical transition and ignores old CUE data");
  check(!cue.changed(discTwo,cueState(discTwo,0)),"new disc advertisement cannot trigger a duplicate eject");
  gate.ready(discTwo,cover,true,true,now);
  check(gate.ready(discTwo,cover,true,true,now+TrackEntryGate.QUIET_NS),"cross-disc eject is consumed once");
  check(!cue.changed(discTwo,"bad value")&&!cue.changed(discTwo,cueState(discTwo,-1)),
      "malformed CUE state has no effect");
  cue.reset();
  check(!cue.changed(discTwo,cueState(discTwo,750)),"reopening the page only binds the current segment");
 }

 private static String cueState(String audio,long frame)throws Exception{
  var digest=MessageDigest.getInstance("SHA-256").digest(audio.getBytes(StandardCharsets.UTF_8));
  return HexFormat.of().formatHex(digest)+":"+frame+":66564bb7-4e6e-4d09-a89a-0834b3cd3f41";
 }
}
