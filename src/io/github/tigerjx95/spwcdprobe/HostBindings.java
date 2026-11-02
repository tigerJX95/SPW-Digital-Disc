package io.github.tigerjx95.spwcdprobe;
import androidx.compose.runtime.*;
import androidx.compose.ui.Modifier;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import java.lang.reflect.Method;
/** Cache only public method bindings selected by the complete host profile. */
final class HostBindings {
 final HostCompatibility.Profile profile;
 final Method cover,local;
 HostBindings(HostCompatibility.Profile profile,ClassLoader loader){
  this.profile=profile;
  try{
   cover=Class.forName(profile.owner(),false,loader).getMethod("\u037f",Function0.class,boolean.class,boolean.class,boolean.class,Modifier.class,Composer.class,int.class,int.class);
   local=Class.forName(profile.local(),false,loader).getMethod("\u037f");
   if(!ProvidableCompositionLocal.class.isAssignableFrom(local.getReturnType()))throw new NoSuchMethodException("sheet composition local");
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Host "+profile.version()+" method binding failed",e);}
 }
 CompositionLocal<?> sheetLocal(){try{return (CompositionLocal<?>)local.invoke(null);}catch(ReflectiveOperationException e){throw new IllegalStateException("Host sheet local unavailable",e);}}
 void drawOriginal(Function0<Unit> toggle,boolean lyrics,boolean a,boolean b,Modifier modifier,Composer composer){
  try{cover.invoke(null,toggle,lyrics,a,b,modifier,composer,0,0);}catch(ReflectiveOperationException e){throw new IllegalStateException("Host cover draw failed",e);}
 }
}
