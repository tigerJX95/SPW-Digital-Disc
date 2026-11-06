package io.github.tigerjx95.spwcdprobe;

import java.security.MessageDigest;
import java.util.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;

/** Recognize the cover painter without depending on its obfuscated identifiers. */
final class CoverPainterIdentity {
    // Both verified 1.19.0 and 1.19.3 implementations produce this normalized hash.
    private static final String VERIFIED = "d1eb28910b980960983b19abbfa73528230bc1f7154819005ff18bd522f8ce05";
    private static final String PAINTER = "androidx/compose/ui/graphics/painter/Painter";
    private static final Set<String> FIELDS = Set.of(
            "Lorg/jetbrains/skia/Shader;", "Landroidx/compose/ui/graphics/ShaderBrush;", "J");

    static boolean candidate(ClassReader reader) {
        if (!PAINTER.equals(reader.getSuperName())
                || !Arrays.asList(reader.getInterfaces()).equals(List.of("java/lang/AutoCloseable"))
                || (reader.getAccess() & (Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT)) != Opcodes.ACC_FINAL) return false;
        var fields = new HashSet<String>();
        boolean[] valid = {true};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                if (access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
                        || !FIELDS.contains(desc) || !fields.add(desc)) valid[0] = false;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return valid[0] && fields.equals(FIELDS);
    }

    static String fingerprint(ClassReader reader) {
        if (!candidate(reader)) throw new IllegalStateException("Unexpected cover painter structure");
        String self = reader.getClassName();
        var declaredNames = new HashMap<String, String>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                declaredNames.put(desc, name);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        var writer = new ClassWriter(0);
        var remapper = new ClassRemapper(writer, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                return self.equals(name) ? "spw/VerifiedCoverPainter" : name;
            }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                if (!self.equals(owner)) return name;
                if (!name.equals(declaredNames.get(descriptor))) {
                    throw new IllegalStateException("Unknown cover painter field reference");
                }
                return switch (descriptor) {
                    case "Lorg/jetbrains/skia/Shader;" -> "shader";
                    case "Landroidx/compose/ui/graphics/ShaderBrush;" -> "brush";
                    case "J" -> "size";
                    default -> throw new IllegalStateException("Unexpected painter field reference");
                };
            }
        });
        reader.accept(new ClassVisitor(Opcodes.ASM9, remapper) {
            @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                // Kotlin's source names do not describe the obfuscated JVM names.
                return desc.equals("Lkotlin/Metadata;") ? null : super.visitAnnotation(desc, visible);
            }
        }, ClassReader.SKIP_DEBUG);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void verify(ClassReader reader) {
        if (!VERIFIED.equals(fingerprint(reader))) {
            throw new IllegalStateException("Cover painter implementation fingerprint differs: " + reader.getClassName());
        }
    }

    private CoverPainterIdentity() {}
}
