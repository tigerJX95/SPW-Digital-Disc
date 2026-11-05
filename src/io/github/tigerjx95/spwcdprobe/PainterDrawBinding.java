package io.github.tigerjx95.spwcdprobe;

import androidx.compose.ui.graphics.ColorFilter;
import androidx.compose.ui.graphics.drawscope.DrawScope;
import androidx.compose.ui.graphics.painter.Painter;
import androidx.compose.ui.layout.LayoutCoordinates;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** Cached Painter bindings shared by Hook, rendering and cover capture. */
final class PainterDrawBinding {
    static final Method METHOD = resolve(Painter.class);
    static final Method INTRINSIC_SIZE = resolvePackedSize(Painter.class);
    static final Method LAYOUT_SIZE = resolvePackedSize(LayoutCoordinates.class);
    static final List<String> PARAMETERS = Arrays.stream(METHOD.getParameterTypes())
            .map(Class::getName).toList();

    private PainterDrawBinding() {}

    static Method resolvePackedSize(Class<?> owner) {
        Method match = null;
        for (Method method : owner.getDeclaredMethods()) {
            int flags = method.getModifiers();
            if (!Modifier.isPublic(flags) || !Modifier.isAbstract(flags) || method.isSynthetic()
                    || method.getReturnType() != long.class || method.getParameterCount() != 0) continue;
            if (match != null) throw new IllegalStateException("Ambiguous packed size signature: " + owner.getName());
            match = method;
        }
        if (match == null) throw new IllegalStateException("Missing packed size signature: " + owner.getName());
        return match;
    }

    static Method resolve(Class<?> owner) {
        Class<?>[] parameters = {DrawScope.class, long.class, float.class, ColorFilter.class};
        Method match = null;
        for (Method method : owner.getDeclaredMethods()) {
            int flags = method.getModifiers();
            if (!Modifier.isPublic(flags) || !Modifier.isFinal(flags) || Modifier.isStatic(flags)
                    || method.isBridge() || method.isSynthetic() || method.getReturnType() != void.class
                    || !Arrays.equals(method.getParameterTypes(), parameters)) continue;
            if (match != null) throw new IllegalStateException("Ambiguous Painter draw signature: " + owner.getName());
            match = method;
        }
        if (match == null) throw new IllegalStateException("Missing Painter draw signature: " + owner.getName());
        return match;
    }
}
