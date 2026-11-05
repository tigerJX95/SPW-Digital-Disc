package io.github.tigerjx95.spwcdprobe;
import com.xuncorp.spw.workshop.api.WorkshopApi;
import com.xuncorp.spw.workshop.api.hook.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import net.bytebuddy.jar.asm.*;
/** Observes the host's final artwork publication, after its current-track check.
 * No image bytes, filename heuristics or independent artwork decoder are used. */
final class HostCoverStatus {
 static final String TASK="com.xuncorp.voxzen.service.PlaybackService$updateImageBitmap$2";
 private final Map<Object,CoverAvailability.Ticket> tickets=Collections.synchronizedMap(new WeakHashMap<>());
 private final CoverAvailability state;
 private final Supplier<String> current;
 private final Field media,owner,label,bitmap,service;
 private final Method path,refresh;
 HostCoverStatus(CoverAvailability state,Supplier<String> current)throws Exception{
  this.state=state;this.current=current;
  ClassLoader loader=getClass().getClassLoader();
  verify(loader,TASK,"2bbbcb1e1c3a0ac53924a7618e23a9861a3e9de65cd658365026d1049d2ccadd");
  boolean old=HostCompatibility.current().version().equals("1.19.0");
  verify(loader,"com.xuncorp.voxzen.service.PlaybackService",old?"b2beaca0ace6a8672025bef5b7e335027686d3253f77f999563bf02ffba54b5f":"8a3433c8807fc38556143f7949af55736d038516888dea72893fcd0a5c2d1b9e");
  verify(loader,"com.xuncorp.voxzen.service.PlaybackService$refreshCurrentCover$1","0ff9fd7de23f99d4b38c89cab20b1d7cb2f86535f562b32cca415571e14d462f");
  HostCompatibility.current().verifyPlayback(loader);
  Class<?> task=Class.forName(TASK,false,loader);
  media=field(task,"$mediaItem");owner=field(task,"this$0");label=field(task,"label");
  path=resolveArtworkPath(loader,task,media.getType());
  bitmap=field(owner.getType(),"imageBitmapOwner");refresh=owner.getType().getMethod("refreshCurrentCover");
  service=field(Class.forName("com.xuncorp.voxzen.service.PlaybackController",false,loader),"service");
 }
 private static Method resolveArtworkPath(ClassLoader loader,Class<?> task,Class<?> item)throws Exception{
  String itemName=net.bytebuddy.jar.asm.Type.getInternalName(item);
  var calls=new ArrayList<String>();
  try(var in=loader.getResourceAsStream(task.getName().replace('.','/')+".class")){
   if(in==null)throw new IllegalStateException("Missing artwork task class");
   new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9){
    @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
     if(!name.equals("invokeSuspend")||!desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))return null;
     return new MethodVisitor(Opcodes.ASM9){
      @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf){
       if(opcode==Opcodes.INVOKEVIRTUAL&&!itf&&owner.equals(itemName)&&desc.equals("()Ljava/lang/String;"))
        calls.add(name);
      }
     };
    }
   },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
  }
  // In the verified artwork task, the only media string getter supplies the image reader's path.
  if(calls.size()!=1)throw new IllegalStateException("Expected one artwork path call, found "+calls.size());
  Method method=item.getDeclaredMethod(calls.get(0));
  int flags=method.getModifiers();
  if(!Modifier.isPublic(flags)||Modifier.isStatic(flags)||method.getReturnType()!=String.class)
   throw new IllegalStateException("Invalid artwork path getter signature");
  return method;
 }
 HookHandle install(){
  return WorkshopApi.hookRegistrar().hookMethod(TASK,"invokeSuspend",List.of("java.lang.Object"),new MethodHook(50){
   @Override public void before(MethodHookParam call){
    try{Object task=call.getThisObject();if(label.getInt(task)==0){Object item=media.get(task);
     CoverAvailability.Ticket ticket=state.begin(item==null?null:(String)path.invoke(item),current.get(),System.nanoTime());
     if(ticket!=null)tickets.put(task,ticket);
    }}catch(ReflectiveOperationException|RuntimeException ignored){}
   }
   @Override public void after(MethodHookParam call){
    Object task=call.getThisObject();
    if(call.getThrowable()!=null){tickets.remove(task);return;}
    // A suspended coroutine has not produced a result, even if it took seconds.
    if(call.getResult()!=kotlin.Unit.INSTANCE)return;
    CoverAvailability.Ticket ticket=tickets.remove(task);
    if(ticket==null)return;
    try{state.complete(ticket,current.get(),bitmap.get(owner.get(task))==null);}
    catch(ReflectiveOperationException|RuntimeException ignored){}
   }
  });
 }
 void refreshCurrent()throws Exception{Object value=service.get(null);if(value!=null)refresh.invoke(value);}
 static void verify(ClassLoader loader,String name,String expected)throws Exception{
  try(var in=loader.getResourceAsStream(name.replace('.','/')+".class")){
   if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(expected))throw new IllegalStateException("Artwork interface fingerprint differs: "+name);
  }
 }
 private static Field field(Class<?> type,String name)throws Exception{Field value=type.getDeclaredField(name);value.setAccessible(true);return value;}
}
