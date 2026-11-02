package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.NodeCoordinator;
import java.util.*;
/** Live native cover nodes; no painter snapshot survives a track change. */
final class HostCoverBridge {
    // SPW's loading/error artwork is a vector/ColorPainter. Only a decoded
    // bitmap painter or its native mipmap shader may reveal the CD layer.
    static boolean imageReady(Object painter){
        // The pinned host's real loaded covers use MipmapCoverPainter (ƙ).
        // painterResource loading/error artwork can itself be a BitmapPainter,
        // so merely finding ImageBitmap is not proof that the album is ready.
        if(painter==null||!painter.getClass().getName().equals(HostCompatibility.current().painter()))return false;
        for(Class<?> type=painter.getClass();type!=null;type=type.getSuperclass()){
            for(var field:type.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                if(field.getType()==org.jetbrains.skia.Shader.class)try{
                    field.setAccessible(true);if(field.get(painter) instanceof org.jetbrains.skia.Shader shader&&!shader.isClosed())return true;
                }catch(ReflectiveOperationException|RuntimeException ignored){}
            }
        }
        return false;
    }
    record Image(Object painter,LayoutCoordinates coordinates,long area){}
    static DiscImage capture(Object painter)throws Exception{
        if(!imageReady(painter))throw new IllegalStateException("No decoded host cover");
        long size=(Long)androidx.compose.ui.graphics.painter.Painter.class.getMethod("getIntrinsicSize-NH-jbRc").invoke(painter);
        float w=RootRenderer.x(size),h=RootRenderer.y(size);
        if(!Float.isFinite(w)||!Float.isFinite(h)||w<=0||h<=0)throw new IllegalStateException("Invalid host cover size");
        for(Class<?> type=painter.getClass();type!=null;type=type.getSuperclass())for(var field:type.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers())||field.getType()!=org.jetbrains.skia.Shader.class)continue;
            field.setAccessible(true);return DiscImage.capture((org.jetbrains.skia.Shader)field.get(painter),w,h);
        }
        throw new IllegalStateException("No host cover shader");
    }
    static List<Image> images(LayoutCoordinates root){
        List<Image> images=new ArrayList<>();
        if(root instanceof NodeCoordinator coordinator)collect(coordinator.getLayoutNode(),images);
        // AnimatedContent may keep a large outgoing image beside the smaller
        // incoming image. Do not discard the latter: its draw is the wake-up
        // signal for a newly loaded cover. Prefer large nodes only as fallback.
        images.sort(Comparator.comparingLong(Image::area).reversed());
        return images;
    }
    private static void collect(LayoutNode node,List<Image> images){
        if(!node.isAttached())return;
        node.getModifier().any(element->{
            if(element.getClass().getName().contains("PainterElement"))try{
                var getter=element.getClass().getDeclaredMethod("getPainter");getter.setAccessible(true);
                images.add(new Image(getter.invoke(element),node.getCoordinates(),(long)node.getWidth()*node.getHeight()));
            }catch(ReflectiveOperationException | RuntimeException ignored){}
            return false;
        });
        for(LayoutNode child:node.getChildren$ui())collect(child,images);
    }
    static void invalidate(LayoutCoordinates root){
        if(root instanceof NodeCoordinator coordinator)invalidate(coordinator.getLayoutNode());
    }
    private static void invalidate(LayoutNode node){
        if(!node.isAttached())return;
        // Restore root/reflection caches too, not only the Image's own layer.
        node.getInnerCoordinator$ui().invalidateLayer();
        for(LayoutNode child:node.getChildren$ui())invalidate(child);
    }
}
