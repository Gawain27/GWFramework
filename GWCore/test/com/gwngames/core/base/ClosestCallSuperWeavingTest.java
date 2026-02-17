package com.gwngames.core.base;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.CoreModule;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.util.ClosestScan;
import com.gwngames.core.util.ClosestWeaver;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that:
 *  1) The class extending ClosestComponent is rewritten to extend the lower impl.
 *  2) callSuper() forwards CURRENT locals to the lower method.
 *  3) callSuperReplaceArgs(Object...) forwards PROVIDED arguments (with cast/unbox) to the lower method.
 *  4) Works for reference/primitive/void returns.
 */
public class ClosestCallSuperWeavingTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        URL classesUrl = BaseTest.class.getProtectionDomain().getCodeSource().getLocation();
        try (TestWeavingLoader loader = new TestWeavingLoader(new URL[]{classesUrl}, getClass().getClassLoader())) {
            String higherName = this.getClass().getName() + "$PippoHigher";
            Class<?> higherClz = loader.loadClass(higherName);

            // 1) Superclass rewrite sanity
            assertTrue(higherClz.getSuperclass().getName().endsWith("$PippoLower"),
                "Superclass should be PippoLower after weaving");

            Object inst = higherClz.getDeclaredConstructor().newInstance();

            // Helpers to invoke protected methods reflectively
            Method mWho   = higherClz.getDeclaredMethod("who");
            mWho.setAccessible(true);
            Method mSum   = higherClz.getDeclaredMethod("sum", int.class, int.class);
            mSum.setAccessible(true);
            Method mMul2  = higherClz.getDeclaredMethod("mul2", int.class);
            mMul2.setAccessible(true);
            Method mApp   = higherClz.getDeclaredMethod("append", StringBuilder.class, String.class);
            mApp.setAccessible(true);

            // 2) callSuper(): should equal LOWER.who()
            String who = (String) mWho.invoke(inst);
            assertEquals("LOWER", who, "callSuper() should invoke lower.who()");

            // 3) callSuperReplaceArgs: change args (1,2) -> (2,4), then add 10
            int sum = (int) mSum.invoke(inst, 1, 2);
            assertEquals(16, sum, "sum(1,2): (2+4)=6, +10 => 16");

            // 4) callSuper() uses CURRENT locals (after modification)
            //    mul2(x): higher does x += 3; callSuper() => lower.mul2(x)
            int mul = (int) mMul2.invoke(inst, 5);
            assertEquals(16, mul, "mul2(5): (5+3)*2 => 16");

            // 5) void-return + replace args (append)
            StringBuilder sb = new StringBuilder();
            mApp.invoke(inst, sb, "x"); // higher passes "x!" to the super
            assertEquals("[x!]", sb.toString(), "append should reflect replaced argument to lower impl");
        }
    }

    /* ======================= Fixtures ======================= */

    @Init(component = CoreComponent.POPPO, module = DefaultModule.INTERFACE)
    public interface IPoppo extends IBaseComp {}
    /**
     * Lower implementation (the "real" super after weaving).
     */
    @Init(subComp = CoreSubComponent.NONE, module = CoreModule.CORE)
    public static class PippoLower extends BaseComponent implements IPoppo {
        protected String who() { return "LOWER"; }
        protected int    sum(int a, int b) { return a + b; }
        protected int    mul2(int x) { return x * 2; }
        protected void   append(StringBuilder sb, String s) { sb.append('[').append(s).append(']'); }
    }

    /**
     * Higher implementation extending ClosestComponent at compile-time.
     * The loader rewrites its super to PippoLower and rewrites the marker calls.
     */
    @Init(subComp = CoreSubComponent.NONE, module = DefaultModule.TEST)
    public static class PippoHigher extends ClosestComponent implements IPoppo{
        protected String who() {
            // Should be rewritten to invokespecial PippoLower.who()
            return callSuper();
        }
        protected int sum(int a, int b) {
            // Replace args: (a+1, b+2), then add 10 to whatever LOWER returns
            int base = callSuperReplaceArgs(a + 1, b + 2);
            return base + 10;
        }
        protected int mul2(int x) {
            // Modify local then use callSuper() which should pick CURRENT local
            x += 3;
            return callSuper(); // calls lower.mul2(x)
        }
        protected void append(StringBuilder sb, String s) {
            // Pass a modified argument to the lower method
            callSuperReplaceArgs(sb, s + "!");
        }
    }

    /* ======================= Test Loader ======================= */

    /**
     * Child-first loader that:
     *  - scans class bytes with ClosestScan
     *  - validates super-ctor descriptors
     *  - weaves superclass + callSuper markers via ClosestWeaver
     */
    static final class TestWeavingLoader extends URLClassLoader {
        private final ClosestWeaver weaver = new ClosestWeaver();
        private final String testPrefix;

        TestWeavingLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.testPrefix = ClosestCallSuperWeavingTest.class.getName() + "$";
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(testPrefix)) {
                Class<?> already = findLoadedClass(name);
                if (already != null) return already;
                try {
                    Class<?> c = findClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException ignore) { /* fall through */ }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            try (InputStream in = getResourceAsStream(path)) {
                if (in == null) throw new ClassNotFoundException(name);
                byte[] bytes = in.readAllBytes();

                if (name.endsWith("$PippoHigher")) {
                    ClosestScan scan = ClosestScan.of(bytes);
                    if (scan.isClosest()) {
                        String lowerInt = (ClosestCallSuperWeavingTest.class.getName() + "$PippoLower")
                            .replace('.', '/');

                        // Validate invoked super ctors (if any)
                        List<String> ctors = scan.invokedSuperCtorDescs();
                        if (ctors.isEmpty()) {
                            ensureCompatibleConstructor(lowerInt, "()V");
                        } else {
                            for (String desc : ctors) ensureCompatibleConstructor(lowerInt, desc);
                        }

                        // Weave: change super + rewrite callSuper markers
                        bytes = weaver.weave(bytes, lowerInt);
                    }
                }

                return defineClass(name, bytes, 0, bytes.length);
            } catch (Exception e) {
                throw new ClassNotFoundException("Failed to define " + name, e);
            }
        }

        private void ensureCompatibleConstructor(String lowerInternal, String ctorDesc) throws Exception {
            String cn = lowerInternal.replace('/', '.');
            Class<?> superClz = Class.forName(cn, false, getParent());
            Class<?>[] params = Descriptor.toParamTypes(ctorDesc, getParent());
            try {
                Constructor<?> c = superClz.getDeclaredConstructor(params);
                c.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Chosen super " + superClz.getName()
                    + " lacks constructor with descriptor " + ctorDesc);
            }
        }
    }

    /* ======================= Descriptor helper ======================= */

    static final class Descriptor {
        static Class<?>[] toParamTypes(String methodDescriptor, ClassLoader cl) throws ClassNotFoundException {
            int i = 0;
            if (methodDescriptor.charAt(i) != '(') throw new IllegalArgumentException(methodDescriptor);
            i++;
            java.util.ArrayList<Class<?>> out = new java.util.ArrayList<>();
            while (methodDescriptor.charAt(i) != ')') {
                char c = methodDescriptor.charAt(i++);
                switch (c) {
                    case 'Z' -> out.add(boolean.class);
                    case 'B' -> out.add(byte.class);
                    case 'C' -> out.add(char.class);
                    case 'S' -> out.add(short.class);
                    case 'I' -> out.add(int.class);
                    case 'F' -> out.add(float.class);
                    case 'J' -> out.add(long.class);
                    case 'D' -> out.add(double.class);
                    case 'L' -> {
                        int semi = methodDescriptor.indexOf(';', i);
                        String cn = methodDescriptor.substring(i, semi).replace('/', '.');
                        out.add(Class.forName(cn, false, cl));
                        i = semi + 1;
                    }
                    case '[' -> {
                        int start = i - 1;
                        while (methodDescriptor.charAt(i) == '[') i++;
                        char t = methodDescriptor.charAt(i);
                        if (t == 'L') {
                            int semi = methodDescriptor.indexOf(';', i);
                            i = semi + 1;
                        } else i++;
                        String jvmName = methodDescriptor.substring(start, i).replace('/', '.');
                        out.add(Class.forName(jvmName, false, cl));
                    }
                    default -> throw new IllegalArgumentException("Bad descriptor: " + methodDescriptor);
                }
            }
            return out.toArray(Class[]::new);
        }
    }
}
