package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.voxzen.ui.component.PlayerSheetState;
import net.bytebuddy.jar.asm.*;
import java.io.IOException;
import java.net.JarURLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads class metadata without loading candidate classes. */
final class HostClassDiscovery {
    private static final List<String> MARKERS = List.of(
            "FullScreenCover", "FloatPlaybackCover", "LocalPlayerSheetState");

    record Targets(String owner, String caller, String local) {}

    private static final ClassValue<Targets> TARGETS = new ClassValue<>() {
        @Override protected Targets computeValue(Class<?> anchor) {
            try {
                return discover(archiveOf(anchor));
            } catch (Exception error) {
                throw new IllegalStateException("Host class discovery failed", error);
            }
        }
    };

    static Targets current() { return TARGETS.get(PlayerSheetState.class); }

    private static Path archiveOf(Class<?> anchor) throws Exception {
        var resource = anchor.getResource(anchor.getSimpleName() + ".class");
        if (resource != null && resource.openConnection() instanceof JarURLConnection jar) {
            return Path.of(jar.getJarFileURL().toURI());
        }
        var source = anchor.getProtectionDomain().getCodeSource();
        if (source == null) throw new IOException("Host archive location unavailable");
        return Path.of(source.getLocation().toURI());
    }

    static Targets discover(Path archive) throws IOException {
        var candidates = new HashMap<String, String>();
        long scannedBytes = 0;
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String path = entry.getName();
                if (!path.endsWith(".class") || path.contains("$")
                        || !(path.startsWith("androidx/compose/ui/") || path.startsWith("com/xuncorp/voxzen/ui/"))) continue;
                if (entry.getSize() > 1024 * 1024) throw new IOException("Oversized class: " + path);
                byte[] bytes;
                try (var in = zip.getInputStream(entry)) {
                    bytes = in.readNBytes(1024 * 1024 + 1);
                }
                if (bytes.length > 1024 * 1024) throw new IOException("Oversized class: " + path);
                scannedBytes += bytes.length;
                if (scannedBytes > 128L * 1024 * 1024) throw new IOException("Host discovery scan limit exceeded");
                var reader = new ClassReader(bytes);
                for (String marker : metadataMarkers(reader)) {
                    if (!path.equals(reader.getClassName() + ".class")) {
                        throw new IOException("Metadata candidate name differs from archive entry: " + path);
                    }
                    String existing = candidates.putIfAbsent(marker, reader.getClassName().replace('/', '.'));
                    if (existing != null) throw new IOException("Ambiguous Kotlin Metadata marker: " + marker);
                }
            }
        }
        for (String marker : MARKERS) {
            if (!candidates.containsKey(marker)) throw new IOException("Missing Kotlin Metadata marker: " + marker);
        }
        return new Targets(candidates.get(MARKERS.get(0)), candidates.get(MARKERS.get(1)), candidates.get(MARKERS.get(2)));
    }

    private static Set<String> metadataMarkers(ClassReader reader) {
        var markers = new HashSet<String>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals("Lkotlin/Metadata;")) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitArray(String name) {
                        if (!name.equals("d2")) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public void visit(String key, Object value) {
                                if (MARKERS.contains(value)) markers.add((String) value);
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return markers;
    }

    static void verifyCall(ClassLoader loader, String caller, java.lang.reflect.Method cover) throws IOException {
        String owner = Type.getInternalName(cover.getDeclaringClass());
        String descriptor = Type.getMethodDescriptor(cover);
        int[] matches = {0};
        boolean[] invalidCall = {false};
        String callerName = caller.replace('.', '/');
        try (var in = loader.getResourceAsStream(callerName + ".class")) {
            if (in == null) throw new IOException("Missing Hook caller: " + caller);
            var reader = new ClassReader(in);
            if (!callerName.equals(reader.getClassName())) {
                throw new IOException("Hook caller name differs from class resource: " + caller);
            }
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String target, String method, String desc, boolean itf) {
                            if (owner.equals(target) && cover.getName().equals(method) && descriptor.equals(desc)) {
                                matches[0]++;
                                if (opcode != Opcodes.INVOKESTATIC || itf) invalidCall[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        if (matches[0] != 1 || invalidCall[0]) {
            throw new IOException("Expected one static cover call in " + caller + ", found " + matches[0]
                    + (invalidCall[0] ? " (invalid invocation)" : ""));
        }
    }
}
