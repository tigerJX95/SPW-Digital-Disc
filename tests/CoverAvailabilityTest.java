package io.github.tigerjx95.spwcdprobe;
public class CoverAvailabilityTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var s=new CoverAvailability();long t=1_000_000_000L;
  s.observe("A",t);
  check(!s.missing("A"),"unknown is not absent");
  var a=s.begin("A","A",t);
  check(!s.refreshNeeded("A",t+99_000_000_000L)&&!s.missing("A"),"slow read never times out to silver");
  s.complete(a,"A",true);check(s.missing("A"),"completed empty read");
  s.observe("B",t);check(!s.missing("B"),"switch clears absence");
  s.complete(a,"B",true);check(!s.missing("B"),"late A cannot affect B");
  var b=s.begin("B","B",t);var newer=s.begin("B","B",t);
  s.complete(newer,"B",false);s.complete(b,"B",true);
  check(!s.missing("B"),"late older read cannot override successful cover");
  s.observe("A",t);s.complete(a,"A",true);check(!s.missing("A"),"A-B-A generation guard");
  check(!s.refreshNeeded("A",t+999_000_000L),"bootstrap waits");
  check(s.refreshNeeded("A",t+1_000_000_000L),"bootstrap once");
  check(!s.refreshNeeded("A",t+9_000_000_000L)&&!s.missing("A"),"bootstrap is not absence");
  var refreshed=s.begin("A","A",t);s.complete(refreshed,"A",true);
  check(s.missing("A"),"bootstrap can publish missing");
  s.reset();s.complete(refreshed,"A",true);check(!s.missing("A"),"stop rejects late task");
  new HostCoverStatus(s,()->null); // Verify actual host fingerprints and all reflective bindings.
  System.out.println("PASS host cover status bindings, no timeout inference, late results, ABA switch and one-shot bootstrap");
 }
}
