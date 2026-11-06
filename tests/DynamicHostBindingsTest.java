package io.github.tigerjx95.spwcdprobe;

import com.xuncorp.spw.workshop.api.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.jar.asm.commons.*;

/** Exercises the normal compatibility and binding entry points after host class renaming. */
public class DynamicHostBindingsTest {
    static final String SHEET = "com.xuncorp.voxzen.ui.component.PlayerSheetState";

    public static void main(String[] args) throws Exception {
        var parent = DynamicHostBindingsTest.class.getClassLoader();
        var original = HostCompatibility.current();
        var names = List.of(original.owner(), original.caller(), original.local(), original.painter());
        var mapping = new HashMap<String, String>();
        for (int i = 0; i < names.size(); i++) {
            mapping.put(names.get(i).replace('.', '/'), "androidx/compose/ui/DynamicTest" + i);
        }
        for (String scenario : List.of("renamed", "missing", "duplicate", "wrong-call", "missing-painter", "duplicate-painter", "changed-painter", "wrong-painter-path", "wrong-painter-field")) {
            Path archive = Files.createTempFile("spw-dynamic-host-", ".jar");
            try {
                try (var out = new ZipOutputStream(Files.newOutputStream(archive))) {
                    try (var in = parent.getResourceAsStream(SHEET.replace('.', '/') + ".class")) {
                        write(out, SHEET.replace('.', '/'), in.readAllBytes());
                    }
                    for (String name : names) {
                        if (scenario.equals("missing") && name.equals(original.local())) continue;
                        if (scenario.equals("missing-painter") && name.equals(original.painter())) continue;
                        byte[] bytes;
                        try (var in = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            bytes = in.readAllBytes();
                        }
                        var writer = new ClassWriter(0);
                        ClassVisitor remapper = new ClassRemapper(writer, new Remapper(Opcodes.ASM9) {
                            @Override public String map(String internalName) {
                                return mapping.getOrDefault(internalName, internalName);
                            }
                            @Override public String mapFieldName(String owner, String field, String descriptor) {
                                return owner.equals(original.painter().replace('.', '/')) ? "renamed_" + field : field;
                            }
                        });
                        if (scenario.equals("wrong-painter-field") && name.equals(original.painter())) {
                            remapper = new ClassVisitor(Opcodes.ASM9, remapper) {
                                @Override public MethodVisitor visitMethod(int flags, String method, String desc,
                                        String signature, String[] exceptions) {
                                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(flags, method, desc, signature, exceptions)) {
                                        @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                                            super.visitFieldInsn(opcode, owner,
                                                    owner.equals(original.painter().replace('.', '/')) ? "missingField" : field,
                                                    descriptor);
                                        }
                                    };
                                }
                            };
                        }
                        if (scenario.equals("changed-painter") && name.equals(original.painter())) {
                            remapper = new ClassVisitor(Opcodes.ASM9, remapper) {
                                @Override public MethodVisitor visitMethod(int flags, String method, String desc,
                                        String signature, String[] exceptions) {
                                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(flags, method, desc, signature, exceptions)) {
                                        @Override public void visitMethodInsn(int opcode, String owner, String target,
                                                String descriptor, boolean itf) {
                                            super.visitMethodInsn(opcode, owner,
                                                    target.equals("getWidth") ? "getHeight" : target, descriptor, itf);
                                        }
                                    };
                                }
                            };
                        }
                        if (scenario.equals("wrong-call") && name.equals(original.caller())) {
                            remapper = new ClassVisitor(Opcodes.ASM9, remapper) {
                                @Override public MethodVisitor visitMethod(int flags, String method, String desc,
                                        String signature, String[] exceptions) {
                                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(flags, method, desc, signature, exceptions)) {
                                        @Override public void visitMethodInsn(int opcode, String owner, String target,
                                                String descriptor, boolean itf) {
                                            super.visitMethodInsn(opcode, owner,
                                                    owner.equals(original.owner().replace('.', '/')) ? "missingTarget" : target,
                                                    descriptor, itf);
                                        }
                                    };
                                }
                            };
                        }
                        new ClassReader(bytes).accept(remapper, 0);
                        String renamed = mapping.get(name.replace('.', '/'));
                        write(out, scenario.equals("wrong-painter-path") && name.equals(original.painter()) ? renamed + "WrongPath" : renamed, writer.toByteArray());
                        if ((scenario.equals("duplicate") && name.equals(original.owner()))
                                || (scenario.equals("duplicate-painter") && name.equals(original.painter()))) {
                            var duplicate = new ClassWriter(0);
                            new ClassReader(writer.toByteArray()).accept(new ClassRemapper(duplicate, new Remapper(Opcodes.ASM9) {
                                @Override public String map(String internalName) {
                                    return internalName.equals(renamed) ? renamed + "Duplicate" : internalName;
                                }
                            }), 0);
                            write(out, renamed + "Duplicate", duplicate.toByteArray());
                        }
                    }
                }
                try (var loader = new URLClassLoader(new URL[]{archive.toUri().toURL()}, parent) {
                    @Override public URL getResource(String name) {
                        URL local = findResource(name);
                        return local != null ? local : super.getResource(name);
                    }
                    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                        synchronized (getClassLoadingLock(name)) {
                            if (!name.equals(SHEET)) return super.loadClass(name, resolve);
                            Class<?> type = findLoadedClass(name);
                            if (type == null) type = findClass(name);
                            if (resolve) resolveClass(type);
                            return type;
                        }
                    }
                }) {
                    try {
                        var profile = HostCompatibility.verify(new PluginContext("test", "1.4.0-test4",
                                ".", "future-build", Channel.MS), loader);
                        var binding = new HostBindings(profile, loader);
                        if (!scenario.equals("renamed")) throw new AssertionError("Accepted " + scenario);
                        if (HostClassDiscovery.current(loader) != HostClassDiscovery.current(loader)) {
                            throw new AssertionError("Discovery result was not cached");
                        }
                        if (!binding.cover.getDeclaringClass().getName().endsWith("DynamicTest0")
                                || !binding.caller.endsWith("DynamicTest1")
                                || !binding.local.getDeclaringClass().getName().endsWith("DynamicTest2")
                                || !profile.painter().endsWith("DynamicTest3")) {
                            throw new AssertionError("Binding did not use renamed classes");
                        }
                    } catch (IllegalStateException failure) {
                        if (scenario.equals("renamed")) throw failure;
                        String expected = switch (scenario) {
                            case "missing" -> "Missing Kotlin Metadata marker";
                            case "duplicate" -> "Ambiguous Kotlin Metadata marker";
                            case "wrong-call" -> "Expected one static cover call";
                            case "missing-painter" -> "Missing cover painter structure";
                            case "duplicate-painter" -> "Ambiguous cover painter structure";
                            case "changed-painter" -> "Cover painter implementation fingerprint differs";
                            case "wrong-painter-path" -> "Painter name differs from archive entry";
                            case "wrong-painter-field" -> "Unknown cover painter field reference";
                            default -> throw new AssertionError(scenario);
                        };
                        boolean matched = false;
                        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                            if (String.valueOf(cause.getMessage()).contains(expected)) matched = true;
                        }
                        if (!matched) throw new AssertionError("Wrong rejection for " + scenario, failure);
                    }
                }
            } finally {
                Files.deleteIfExists(archive);
            }
        }
        System.out.println("PASS dynamic host entry: renamed classes accepted; missing/duplicate metadata, wrong cover call and missing/duplicate/changed/misplaced painter rejected");
    }

    private static void write(ZipOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new ZipEntry(name + ".class"));
        out.write(bytes);
        out.closeEntry();
    }
}
