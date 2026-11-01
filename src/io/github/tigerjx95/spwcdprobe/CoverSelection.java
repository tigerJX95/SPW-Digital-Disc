package io.github.tigerjx95.spwcdprobe;
import java.util.*;
/** Prevents outgoing/incoming native nodes from ping-ponging the displayed cover. */
final class CoverSelection {
 private static final long SETTLE_NS=120_000_000L;
 private final Set<Object> retired=Collections.newSetFromMap(new IdentityHashMap<>());
 private final Set<Object> acceptedThisSong=Collections.newSetFromMap(new IdentityHashMap<>());
 private List<Object> seen=List.of();
 private Object committed,candidate,lastAccepted;
 private long candidateAt;
 private String song;
 private boolean bound;
 void song(String next){
  if(bound&&Objects.equals(song,next))return;
  if(bound){retired.clear();retired.addAll(seen);retired.addAll(acceptedThisSong);if(committed!=null)retired.add(committed);}
  bound=true;song=next;candidate=null;acceptedThisSong.clear();
 }
 Object select(List<Object> ready,Object captured,long now){
  seen=new ArrayList<>(ready);
  if(!contains(ready,committed))committed=null;
  if(ready.isEmpty()){candidate=null;return null;}
  if(committed==null){
   Object fresh=eligible(ready,captured);
   // AnimatedContent may briefly replace a painter, then remove that temporary
   // node and keep the original one. A same-song source is safe to recover once
   // it is the sole live node; retiring it forever strands the native fallback.
   // Do not reopen older-song sources or alternate while both nodes are alive.
   if(fresh==null&&ready.size()==1&&(ready.get(0)==lastAccepted||acceptedThisSong.contains(ready.get(0))))fresh=ready.get(0);
   if(fresh==null)return null;
   commit(fresh);return committed;
  }
  Object next=contains(ready,candidate)&&candidate!=committed&&!retired.contains(candidate)?candidate:eligible(ready,captured);
  if(next==null||next==committed){candidate=null;return committed;}
  if(next!=candidate){candidate=next;candidateAt=now;}
  // With no outgoing decoded node, no ambiguous overlap remains.
  if(ready.size()==1||now-candidateAt>=SETTLE_NS)commit(next);
  return committed;
 }
 private Object eligible(List<Object> ready,Object captured){
  if(captured!=committed&&contains(ready,captured)&&!retired.contains(captured))return captured;
  for(Object value:ready)if(value!=committed&&!retired.contains(value))return value;
  return null;
 }
 private void commit(Object value){if(committed!=null&&committed!=value)retired.add(committed);committed=value;lastAccepted=value;acceptedThisSong.add(value);candidate=null;}
 private static boolean contains(List<Object> values,Object value){if(value==null)return false;for(Object item:values)if(item==value)return true;return false;}
}
