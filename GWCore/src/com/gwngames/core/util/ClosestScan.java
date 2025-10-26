package com.gwngames.core.util;

import groovyjarjarasm.asm.AnnotationVisitor;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static groovyjarjarasm.asm.Opcodes.ASM9;
import static groovyjarjarasm.asm.Opcodes.INVOKESPECIAL;

public final class ClosestScan {
    /* ---------- Internal names / descriptors ---------- */
    private static final String CLOSEST_COMPONENT_INT =
        "com/gwngames/core/base/ClosestComponent";
    private static final String CLOSEST_OVER_ANNOTATION_DESC =
        "Lcom/gwngames/core/base/cfg/ClosestOver;";

    // @Init is in api.build per your codebase
    private static final String INIT_ANNOTATION_DESC =
        "Lcom/gwngames/core/api/build/Init;";

    // Enum constant names we treat as “AUTO”
    private static final String ENUM_AUTO = "AUTO";

    /* ---------- Collected state ---------- */
    private String thisName;
    private String superName;
    private final List<String> interfaces = new ArrayList<>();

    // @ClosestOver hints
    private boolean annotatedClosestOver = false;
    private String hintComponentEnumSimple = null; // e.g. "PIPPO"
    private String hintSubEnumSimple = null;       // e.g. "NONE"

    // @Init values (enum simple names). component/sub may be AUTO.
    private String initModuleEnumSimple = null;         // e.g. "NEEDLE_OF_SILVER"
    private String initComponentEnumSimple = null;      // e.g. "PIPPO" or "AUTO"
    private String initSubEnumSimple = null;            // e.g. "NONE" or "AUTO"

    // All super-ctor descriptors actually invoked by our constructors
    private final LinkedHashSet<String> invokedSuperCtorDescs = new LinkedHashSet<>();

    private ClosestScan() {}

    /** Parse the class bytes with a single light ASM pass. */
    public static ClosestScan of(byte[] classBytes) {
        ClosestScan scan = new ClosestScan();

        ClassReader cr = new ClassReader(classBytes);
        cr.accept(new ClassVisitor(ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] ifaces) {
                scan.thisName = name;
                scan.superName = superName;
                if (ifaces != null) scan.interfaces.addAll(Arrays.asList(ifaces));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (CLOSEST_OVER_ANNOTATION_DESC.equals(desc)) {
                    scan.annotatedClosestOver = true;
                    return new AnnotationVisitor(ASM9) {
                        @Override
                        public void visitEnum(String name, String descriptor, String value) {
                            if ("component".equals(name)) {
                                scan.hintComponentEnumSimple = value; // enum constant simple name
                            } else if ("sub".equals(name)) {
                                scan.hintSubEnumSimple = value;
                            }
                        }
                    };
                } else if (INIT_ANNOTATION_DESC.equals(desc)) {
                    return new AnnotationVisitor(ASM9) {
                        @Override
                        public void visitEnum(String name, String descriptor, String value) {
                            // name ∈ { "module", "component", "subComp" }
                            if ("module".equals(name)) {
                                scan.initModuleEnumSimple = value;
                            } else if ("component".equals(name)) {
                                scan.initComponentEnumSimple = value;
                            } else if ("subComp".equals(name)) {
                                scan.initSubEnumSimple = value;
                            }
                        }
                    };
                }
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                if ("<init>".equals(name)) {
                    return new MethodVisitor(ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName,
                                                    String mDesc, boolean itf) {
                            if (opcode == INVOKESPECIAL
                                && "<init>".equals(mName)
                                && owner.equals(scan.superName)) {
                                scan.invokedSuperCtorDescs.add(mDesc);
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                        }
                    };
                }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return scan;
    }

    /* ---------- Public API for the transformer ---------- */

    /** True if the class opted in: extends ClosestComponent or has @ClosestOver. */
    public boolean isClosest() {
        return CLOSEST_COMPONENT_INT.equals(superName) || annotatedClosestOver;
    }

    /** Internal JVM name like com/gwngames/… */
    public String thisInternalName() { return thisName; }
    public String superInternalName() { return superName; }

    /** Implemented interface internal names. */
    public List<String> interfacesInternal() { return interfaces; }

    /** ClosestOver(component=…) enum constant simple name, if present. */
    public Optional<String> hintComponentEnumSimple() { return Optional.ofNullable(hintComponentEnumSimple); }

    /** ClosestOver(sub=…) enum constant simple name, if present. */
    public Optional<String> hintSubEnumSimple() { return Optional.ofNullable(hintSubEnumSimple); }

    /** Init(module=…) enum constant simple name, if present. */
    public Optional<String> initModuleEnumSimple() { return Optional.ofNullable(initModuleEnumSimple); }

    /**
     * Init(component=…) if provided and not AUTO.
     * Returns empty if missing or AUTO (so caller can fall back to @ClosestOver or interface inference).
     */
    public Optional<String> initComponentEnumSimpleIfNotAuto() {
        return (initComponentEnumSimple == null || ENUM_AUTO.equals(initComponentEnumSimple))
            ? Optional.empty()
            : Optional.of(initComponentEnumSimple);
    }

    /**
     * Init(subComp=…) if provided and not AUTO.
     * Returns empty if missing or AUTO (caller may default to NONE).
     */
    public Optional<String> initSubEnumSimpleIfNotAuto() {
        return (initSubEnumSimple == null || ENUM_AUTO.equals(initSubEnumSimple))
            ? Optional.empty()
            : Optional.of(initSubEnumSimple);
    }

    /** All super-ctor descriptors invoked by our constructors (usually one, e.g. "()V"). */
    public List<String> invokedSuperCtorDescs() { return List.copyOf(invokedSuperCtorDescs); }

    /** A convenience: first seen super-ctor descriptor, or empty if none was detected. */
    public Optional<String> firstSuperCtorDesc() {
        if (invokedSuperCtorDescs.isEmpty()) return Optional.empty();
        Iterator<String> it = invokedSuperCtorDescs.iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }
}
