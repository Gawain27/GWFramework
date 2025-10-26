package com.gwngames.core.util;

import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Type;
import groovyjarjarasm.asm.commons.LocalVariablesSorter;

import static groovyjarjarasm.asm.Opcodes.AALOAD;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACONST_NULL;
import static groovyjarjarasm.asm.Opcodes.ALOAD;
import static groovyjarjarasm.asm.Opcodes.ASM9;
import static groovyjarjarasm.asm.Opcodes.ASTORE;
import static groovyjarjarasm.asm.Opcodes.BIPUSH;
import static groovyjarjarasm.asm.Opcodes.CHECKCAST;
import static groovyjarjarasm.asm.Opcodes.DLOAD;
import static groovyjarjarasm.asm.Opcodes.FLOAD;
import static groovyjarjarasm.asm.Opcodes.ICONST_0;
import static groovyjarjarasm.asm.Opcodes.ICONST_1;
import static groovyjarjarasm.asm.Opcodes.ICONST_2;
import static groovyjarjarasm.asm.Opcodes.ICONST_3;
import static groovyjarjarasm.asm.Opcodes.ICONST_4;
import static groovyjarjarasm.asm.Opcodes.ICONST_5;
import static groovyjarjarasm.asm.Opcodes.ICONST_M1;
import static groovyjarjarasm.asm.Opcodes.ILOAD;
import static groovyjarjarasm.asm.Opcodes.INVOKESPECIAL;
import static groovyjarjarasm.asm.Opcodes.INVOKESTATIC;
import static groovyjarjarasm.asm.Opcodes.INVOKEVIRTUAL;
import static groovyjarjarasm.asm.Opcodes.LLOAD;
import static groovyjarjarasm.asm.Opcodes.SIPUSH;


public final class ClosestWeaver {

    private static final String CALLSUPER_NAME = "callSuper";
    private static final String CALLSUPER_DESC = "()Ljava/lang/Object;";
    private static final String CALLSUPER_REP_NAME = "callSuperReplaceArgs";
    private static final String CALLSUPER_REP_DESC = "([Ljava/lang/Object;)Ljava/lang/Object;";

    public byte[] weave(byte[] origBytes, String newSuperInternal) {
        ClassReader cr = new ClassReader(origBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        final String[] originalSuper = new String[1];

        ClassVisitor cv = new ClassVisitor(ASM9, cw) {
            @Override
            public void visit(int v, int acc, String name, String sig, String superName, String[] ifaces) {
                originalSuper[0] = superName;
                super.visit(v, acc, name, sig, newSuperInternal, ifaces);
            }

            @Override
            public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(acc, name, desc, sig, ex);

                // 1) Constructors: retarget <init> invokespecial owner to the new super
                if ("<init>".equals(name)) {
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                            if (opcode == INVOKESPECIAL && "<init>".equals(mName) && owner.equals(originalSuper[0])) {
                                super.visitMethodInsn(INVOKESPECIAL, newSuperInternal, mName, mDesc, false);
                                return;
                            }
                            super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                        }
                    };
                }

                // 2) Instance methods: rewrite callSuper markers by name/desc (ignore owner)
                if ((acc & ACC_STATIC) == 0) {
                    return new CallSuperRewriter(ASM9, mv, acc, name, desc, newSuperInternal);
                }

                return mv;
            }
        };

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /** Rewriter that replaces ClosestComponent marker calls and normalizes to Object. */
    private static final class CallSuperRewriter extends LocalVariablesSorter {
        private final String methodName;
        private final String methodDesc; // the *enclosing* method desc
        private final String newSuper;

        CallSuperRewriter(int api, MethodVisitor mv, int access, String name, String desc, String newSuperInternal) {
            super(api, access, desc, mv);
            this.methodName = name;
            this.methodDesc = desc;
            this.newSuper = newSuperInternal;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            boolean isMarkerCall =
                opcode == INVOKEVIRTUAL &&
                    ((name.equals(CALLSUPER_NAME) && descriptor.equals(CALLSUPER_DESC)) ||
                        (name.equals(CALLSUPER_REP_NAME) && descriptor.equals(CALLSUPER_REP_DESC)));

            if (!isMarkerCall) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            final boolean isReplaceArgs = name.equals(CALLSUPER_REP_NAME);

            if (isReplaceArgs) {
                // Stack: [..., this, array]
                final int arrLocal = newLocal(Type.getType(Object[].class));
                super.visitVarInsn(ASTORE, arrLocal); // store array; keep 'this' on stack

                // Load each arg from array, cast/unbox to real parameter types
                Type[] args = Type.getArgumentTypes(methodDesc);
                for (int i = 0; i < args.length; i++) {
                    super.visitVarInsn(ALOAD, arrLocal);
                    pushInt(i);
                    super.visitInsn(AALOAD);
                    castTopObjectTo(args[i]);
                }
            } else {
                // callSuper(): load CURRENT locals as arguments (this is already on stack)
                Type[] args = Type.getArgumentTypes(methodDesc);
                int slot = 1; // skip 'this'
                for (Type t : args) {
                    loadLocalForType(t, slot);
                    slot += t.getSize();
                }
            }

            // Replace marker with actual super call
            super.visitMethodInsn(INVOKESPECIAL, newSuper, methodName, methodDesc, false);

            // Normalize return to Object to preserve original stack contract
            adaptReturnToObject(Type.getReturnType(methodDesc));
        }

        /* ---------- helpers ---------- */

        private void adaptReturnToObject(Type ret) {
            switch (ret.getSort()) {
                case Type.VOID -> // original marker returned Object; if caller expects to POP, give it something
                    super.visitInsn(ACONST_NULL);
                case Type.BOOLEAN -> box("java/lang/Boolean", "Z", "booleanValue", "()Z", "valueOf", "(Z)Ljava/lang/Boolean;");
                case Type.BYTE    -> box("java/lang/Byte",    "B", "byteValue",    "()B", "valueOf", "(B)Ljava/lang/Byte;");
                case Type.CHAR    -> box("java/lang/Character","C","charValue",    "()C", "valueOf", "(C)Ljava/lang/Character;");
                case Type.SHORT   -> box("java/lang/Short",   "S", "shortValue",   "()S", "valueOf", "(S)Ljava/lang/Short;");
                case Type.INT     -> box("java/lang/Integer", "I", "intValue",     "()I", "valueOf", "(I)Ljava/lang/Integer;");
                case Type.FLOAT   -> box("java/lang/Float",   "F", "floatValue",   "()F", "valueOf", "(F)Ljava/lang/Float;");
                case Type.LONG    -> box("java/lang/Long",    "J", "longValue",    "()J", "valueOf", "(J)Ljava/lang/Long;");
                case Type.DOUBLE  -> box("java/lang/Double",  "D", "doubleValue",  "()D", "valueOf", "(D)Ljava/lang/Double;");
                default /* array/object */ -> {
                    // leave as-is; caller may CHECKCAST and/or POP
                }
            }
        }

        private void box(String owner, String prim, String unwrapName, String unwrapDesc,
                         String valueOf, String valueOfDesc) {
            // stack: primitive
            super.visitMethodInsn(INVOKESTATIC, owner, "valueOf", valueOfDesc, false);
            // now an Object (wrapper) is on stack
        }

        private void loadLocalForType(Type t, int slot) {
            switch (t.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> super.visitVarInsn(ILOAD, slot);
                case Type.FLOAT -> super.visitVarInsn(FLOAD, slot);
                case Type.LONG -> super.visitVarInsn(LLOAD, slot);
                case Type.DOUBLE -> super.visitVarInsn(DLOAD, slot);
                default -> super.visitVarInsn(ALOAD, slot);
            }
        }

        private void pushInt(int v) {
            if (v >= -1 && v <= 5) {
                super.visitInsn(new int[]{ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5}[v + 1]);
            } else if (v <= 127) super.visitIntInsn(BIPUSH, v);
            else if (v <= 32767) super.visitIntInsn(SIPUSH, v);
            else super.visitLdcInsn(v);
        }

        /** Convert top-of-stack Object to target type (used for replace-args). */
        private void castTopObjectTo(Type t) {
            switch (t.getSort()) {
                case Type.BOOLEAN -> { super.visitTypeInsn(CHECKCAST, "java/lang/Boolean"); super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false); }
                case Type.BYTE    -> { super.visitTypeInsn(CHECKCAST, "java/lang/Byte");    super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte",    "byteValue",    "()B", false); }
                case Type.CHAR    -> { super.visitTypeInsn(CHECKCAST, "java/lang/Character");super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character","charValue",    "()C", false); }
                case Type.SHORT   -> { super.visitTypeInsn(CHECKCAST, "java/lang/Short");   super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short",   "shortValue",   "()S", false); }
                case Type.INT     -> { super.visitTypeInsn(CHECKCAST, "java/lang/Integer"); super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue",     "()I", false); }
                case Type.FLOAT   -> { super.visitTypeInsn(CHECKCAST, "java/lang/Float");   super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float",   "floatValue",   "()F", false); }
                case Type.LONG    -> { super.visitTypeInsn(CHECKCAST, "java/lang/Long");    super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long",    "longValue",    "()J", false); }
                case Type.DOUBLE  -> { super.visitTypeInsn(CHECKCAST, "java/lang/Double");  super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double",  "doubleValue",  "()D", false); }
                case Type.ARRAY, Type.OBJECT -> super.visitTypeInsn(CHECKCAST, t.getInternalName());
                case Type.VOID -> { /* not a param */ }
            }
        }
    }
}
