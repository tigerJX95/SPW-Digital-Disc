package io.github.tigerjx95.spwcdprobe;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.NodeCoordinator;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
/** Live native cover nodes; no painter snapshot survives a track change. */
final class HostCoverBridge {
    private static final ClassValue<List<java.lang.reflect.Field>> SHADER_FIELDS = new ClassValue<>() {
        @Override protected List<java.lang.reflect.Field> computeValue(Class<?> painterType) {
            var fields = new ArrayList<java.lang.reflect.Field>();
            for (Class<?> type = painterType; type != null; type = type.getSuperclass()) {
                for (var field : type.getDeclaredFields()) {
                    if (field.getType() == org.jetbrains.skia.Shader.class
                            && !java.lang.reflect.Modifier.isStatic(field.getModifiers())
                            && field.trySetAccessible()) fields.add(field);
                }
            }
            return List.copyOf(fields);
        }
    };

    // Retain the verified painter identity so placeholders never become covers.
    private static org.jetbrains.skia.Shader readyShader(Object painter) {
        if (painter == null || !painter.getClass().getName().equals(HostCompatibility.current().painter())) return null;
        for (var field : SHADER_FIELDS.get(painter.getClass())) {
            try {
                if (field.get(painter) instanceof org.jetbrains.skia.Shader shader && !shader.isClosed()) return shader;
            } catch (IllegalAccessException ignored) {
                // An unreadable field cannot supply artwork.
            }
        }
        return null;
    }

    static boolean imageReady(Object painter) { return readyShader(painter) != null; }

    record Image(Object painter, LayoutCoordinates coordinates, long area) {}

    static DiscImage capture(Object painter) throws Exception {
        var shader = readyShader(painter);
        if (shader == null) throw new IllegalStateException("No decoded host cover");
        long size = (Long) PainterDrawBinding.INTRINSIC_SIZE.invoke(painter);
        float width = RootRenderer.x(size), height = RootRenderer.y(size);
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalStateException("Invalid host cover size");
        }
        return DiscImage.capture(shader, width, height);
    }
    static List<Image> images(LayoutCoordinates root){
        List<Image> images=new ArrayList<>();
        if(root instanceof NodeCoordinator coordinator)visitPainters(coordinator.getLayoutNode(),(painter,node)->{
            images.add(new Image(painter,node.getCoordinates(),(long)node.getWidth()*node.getHeight()));
            return false;
        });
        // AnimatedContent may keep a large outgoing image beside the smaller
        // incoming image. Do not discard the latter: its draw is the wake-up
        // signal for a newly loaded cover. Prefer large nodes only as fallback.
        images.sort(Comparator.comparingLong(Image::area).reversed());
        return images;
    }
    private static final ClassValue<Optional<java.lang.reflect.Method>> PAINTER_GETTERS = new ClassValue<>() {
        @Override protected Optional<java.lang.reflect.Method> computeValue(Class<?> type) {
            if (!type.getName().equals("androidx.compose.ui.draw.PainterElement")) return Optional.empty();
            try {
                var getter = type.getDeclaredMethod("getPainter");
                return getter.getReturnType() == androidx.compose.ui.graphics.painter.Painter.class
                        && !java.lang.reflect.Modifier.isStatic(getter.getModifiers())
                        && getter.trySetAccessible() ? Optional.of(getter) : Optional.empty();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Optional.empty();
            }
        }
    };
    static boolean anyPainter(LayoutCoordinates root,Object painter,Predicate<LayoutCoordinates> matches){
        return root instanceof NodeCoordinator coordinator
                && visitPainters(coordinator.getLayoutNode(),
                        (candidate,node)->candidate==painter&&matches.test(node.getCoordinates()));
    }
    private static boolean visitPainters(LayoutNode node,BiPredicate<Object,LayoutNode> visitor){
        if(!node.isAttached())return false;
        if(node.getModifier().any(element->{
            var getter = PAINTER_GETTERS.get(element.getClass()).orElse(null);
            if(getter != null)try{
                return visitor.test(getter.invoke(element),node);
            }catch(ReflectiveOperationException | RuntimeException ignored){}
            return false;
        }))return true;
        for(LayoutNode child:node.getChildren$ui())if(visitPainters(child,visitor))return true;
        return false;
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
