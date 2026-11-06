package io.github.tigerjx95.spwcdprobe;
import androidx.compose.runtime.*;
import androidx.compose.ui.Modifier;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import java.lang.reflect.Method;
import java.util.Arrays;
/** Cache only public method bindings selected by the complete host profile. */
final class HostBindings {
 final HostCompatibility.Profile profile;
 final Method cover,local;
 final String caller;
 private CompositionLocal<?> sheet;
 HostBindings(HostCompatibility.Profile profile,ClassLoader loader){
  this.profile=profile;
  try{
   var targets=HostClassDiscovery.current(loader);
   if(!targets.owner().equals(profile.owner()) || !targets.caller().equals(profile.caller())
     || !targets.local().equals(profile.local()) || !targets.painter().equals(profile.painter())) {
    throw new IllegalStateException("Discovered classes do not match verified host " + profile.version());
   }
   caller=targets.caller();
   cover=uniqueStatic(Class.forName(targets.owner(),false,loader),void.class,
    Function0.class,boolean.class,boolean.class,boolean.class,Modifier.class,Composer.class,int.class,int.class);
   local=uniqueStatic(Class.forName(targets.local(),false,loader),ProvidableCompositionLocal.class);
   HostClassDiscovery.verifyCall(loader,caller,cover);
   SheetStateBinding.current();
  }catch(ReflectiveOperationException | java.io.IOException e){
   throw new IllegalStateException("Host "+profile.version()+" method binding failed",e);
  }
 }
 static Method uniqueStatic(Class<?> owner,Class<?> result,Class<?>... parameters)throws NoSuchMethodException{
  Method match=null;
  for(Method method:owner.getDeclaredMethods()){
   int flags=method.getModifiers();
   if(!java.lang.reflect.Modifier.isPublic(flags)||!java.lang.reflect.Modifier.isStatic(flags)
     ||!result.isAssignableFrom(method.getReturnType())||!Arrays.equals(parameters,method.getParameterTypes()))continue;
   if(match!=null)throw new NoSuchMethodException("Ambiguous signature in "+owner.getName());
   match=method;
  }
  if(match==null)throw new NoSuchMethodException("Missing signature in "+owner.getName());
  return match;
 }
 CompositionLocal<?> sheetLocal(){
  try{
   if(sheet==null)sheet=(CompositionLocal<?>)local.invoke(null);
   return sheet;
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Host sheet local unavailable",e);}
 }
 void drawOriginal(Function0<Unit> toggle,boolean lyrics,boolean a,boolean b,Modifier modifier,Composer composer){
  try{cover.invoke(null,toggle,lyrics,a,b,modifier,composer,0,0);}catch(ReflectiveOperationException e){throw new IllegalStateException("Host cover draw failed",e);}
 }
}
