package io.github.tigerjx95.spwcdprobe;
public class RevealCycleTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  RevealCycle cycle=new RevealCycle();DiscTransition motion=new DiscTransition();long now=1_000_000_000L;int starts=0;
  for(int round=0;round<100;round++){
   check(cycle.sample(false)==RevealCycle.Edge.HIDDEN,"leaving visible scene arms fresh cycle");motion.reset();
   // Old code spent entry time here despite host not being settled.
   for(int frame=0;frame<120;frame++){now+=16_000_000;check(cycle.sample(false)==RevealCycle.Edge.NONE,"hidden or expanding never starts");motion.tick(now);}
   check(motion.phase()==DiscTransition.Phase.IDLE,"long hidden interval cannot consume animation");
   check(cycle.sample(true)==RevealCycle.Edge.SHOWN,"settled expansion starts new presentation");starts++;motion.enter();
   check(motion.pose().exposed()==0,"cached final CD cannot leak into re-entry");
   // Stale previous pass must be cleared; wait for the next real draw handoff.
   motion.tick(now+2_000_000_000);check(motion.phase()==DiscTransition.Phase.WAITING,"missing handoff does not start clock");
   now+=2_000_000_000;motion.ready(now);motion.tick(now+240_000_000);check(motion.pose().exposed()==0,"cover phase is preserved on reopen");
   check(cycle.sample(true)==RevealCycle.Edge.NONE,"stable recomposition does not restart");motion.tick(now+940_000_000);check(motion.pose().exposed()==1,"each resumed scene receives complete eject");now+=1_000_000_000;
  }
  check(starts==100,"100 mini-player round trips all restart once");
  check(cycle.sample(false)==RevealCycle.Edge.HIDDEN,"interrupted exit hides scene");motion.reset();
  check(cycle.sample(true)==RevealCycle.Edge.SHOWN,"reverse expand gesture also starts a new cycle");motion.enter();check(motion.pose().exposed()==0,"reverse gesture starts with hidden CD");
  cycle.reset();check(cycle.sample(false)==RevealCycle.Edge.HIDDEN,"dispose clears prior visibility");
  System.out.println("PASS 100 settled reopen cycles, hidden/expanding clock exclusion, cleared cached pass contract, cover-before-disc, stable recomposition, interrupted gestures and disposal reset");
 }
}
