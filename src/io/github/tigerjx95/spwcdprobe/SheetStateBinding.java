package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.voxzen.ui.component.PlayerSheetState;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/** The verified sheet class exposes two boolean conditions, both required for display. */
final class SheetStateBinding {
    private final List<Method> conditions;

    private SheetStateBinding(List<Method> conditions) {
        this.conditions = conditions;
    }

    private static final class Cached {
        static final SheetStateBinding VALUE = resolve(PlayerSheetState.class);
    }

    static SheetStateBinding current() { return Cached.VALUE; }

    static SheetStateBinding resolve(Class<?> owner) {
        var conditions = Arrays.stream(owner.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())
                        && !method.isSynthetic()
                        && method.getParameterCount() == 0
                        && method.getReturnType() == boolean.class)
                .toList();
        if (conditions.size() != 2) {
            throw new IllegalStateException("Expected two sheet visibility conditions in "
                    + owner.getName() + ", found " + conditions.size());
        }
        return new SheetStateBinding(conditions);
    }

    boolean visible(Object sheet) {
        try {
            for (Method condition : conditions) {
                if (!Boolean.TRUE.equals(condition.invoke(sheet))) return false;
            }
            return true;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read sheet visibility", error);
        }
    }
}
