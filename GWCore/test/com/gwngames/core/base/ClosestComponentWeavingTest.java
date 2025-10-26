package com.gwngames.core.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.util.ClosestScan;
import com.gwngames.core.util.ClosestWeaver;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that a class extending ClosestComponent can be rewritten at load time
 * to extend the closest lower implementation, and that super->virtual calls
 * made by the lower implementation dispatch to the higher overrides.
 */
public class ClosestComponentWeavingTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        setupApplication(); // your LibGDX stubs etc.

        // Child-first loader that rewrites PippoHigher's superclass to PippoLower
        URL classesUrl = BaseTest.class.getProtectionDomain().getCodeSource().getLocation();
        try (TestWeavingLoader loader = new TestWeavingLoader(new URL[]{classesUrl}, getClass().getClassLoader())) {
            String higherName = this.getClass().getName() + "$PippoHigher";
            Class<?> higherClz = loader.loadClass(higherName); // child-first for our inner fixtures

            // Sanity: superclass was rewritten
            assertTrue(higherClz.getSuperclass().getName().endsWith("$PippoLower"),
                "Superclass should be PippoLower after weaving");

            // Instantiate and call a method defined only on the lower class.
            Object inst = higherClz.getDeclaredConstructor().newInstance();
            String val = (String) higherClz.getMethod("getInitValue").invoke(inst);

            // The lower's ctor calls initValue(); after weaving, that must
            // dispatch to the higher override and store "HIGHER".
            assertEquals("HIGHER", val, "Virtual dispatch from lower -> higher override should succeed");
        }
    }

    /* ======================= Fixtures ======================= */

    /**
     * Pretend "lower module" implementation. Its constructor calls a virtual method.
     */
    @Init(component = ComponentNames.PIPPO, subComp = SubComponentNames.NONE, module = ModuleNames.CORE)
    public static class PippoLower extends BaseComponent {
        private final String value;
        public PippoLower() {
            // This must dispatch to the higher class' override after weaving.
            this.value = initValue();
        }
        /** Virtual hook. */
        protected String initValue() { return "LOWER"; }
        /** Only exists here, not in higher, so we verify it's inherited post-weave. */
        public String getInitValue() { return value; }
    }

    /**
     * Pretend "higher module" implementation. Compiles against ClosestComponent,
     * but the loader will rewrite its super to PippoLower.
     */
    @Init(component = ComponentNames.PIPPO, subComp = SubComponentNames.NONE, module = ModuleNames.TEST)
    public static class PippoHigher extends ClosestComponent {
        // No @Override on purpose: ClosestComponent doesn't declare this method at compile time.
        protected String initValue() { return "HIGHER"; }
        public PippoHigher() { /* implicit super() → post-weave will call PippoLower() */ }
    }

    /* ======================= Test Loader ======================= */

    /**
     * Minimal child-first loader that:
     *  - scans classes with ClosestScan,
     *  - rewrites ClosestComponent-super to PippoLower using ClosestWeaver,
     *  - validates super-ctor compatibility.
     */
    static final class TestWeavingLoader extends URLClassLoader {
        private final ClosestWeaver weaver = new ClosestWeaver();
        private final String testPrefix;

        TestWeavingLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
            // Only child-first for our inner test fixtures to avoid conflicts.
            this.testPrefix = ClosestComponentWeavingTest.class.getName() + "$";
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // Child-first for our fixtures only; otherwise delegate normally.
            if (name.startsWith(testPrefix)) {
                Class<?> already = findLoadedClass(name);
                if (already != null) return already;
                try { return findClass(name); }
                catch (ClassNotFoundException ignore) { /* fall through */ }
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            try (InputStream in = getResourceAsStream(path)) {
                if (in == null) throw new ClassNotFoundException(name);
                byte[] bytes = in.readAllBytes();

                // Only rewrite the "higher" test fixture; others pass through.
                if (name.endsWith("$PippoHigher")) {
                    ClosestScan scan = ClosestScan.of(bytes);
                    if (scan.isClosest()) {
                        String lowerInt = (ClosestComponentWeavingTest.class.getName() + "$PippoLower")
                            .replace('.', '/');

                        // Validate that all invoked super-ctors exist on the target super.
                        List<String> ctors = scan.invokedSuperCtorDescs();
                        if (ctors.isEmpty()) {
                            ensureCompatibleConstructor(lowerInt, "()V");
                        } else {
                            for (String desc : ctors) ensureCompatibleConstructor(lowerInt, desc);
                        }

                        // Rewrite superclass + INVOKESPECIAL targets.
                        bytes = weaver.weave(bytes, lowerInt);
                    }
                }

                return defineClass(name, bytes, 0, bytes.length);
            } catch (Exception e) {
                throw new ClassNotFoundException("Failed to define " + name, e);
            }
        }

        /** Ensure chosen super has a matching constructor signature. */
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

    /* ======================= Small helper for method descriptors ======================= */

    static final class Descriptor {
        static Class<?>[] toParamTypes(String methodDescriptor, ClassLoader cl) throws ClassNotFoundException {
            // Very small descriptor parser for constructors, e.g. ()V, (I)V, (Ljava/lang/String;)V, ([I[[Ljava/lang/Object;)V
            int i = 0;
            if (methodDescriptor.charAt(i) != '(') throw new IllegalArgumentException(methodDescriptor);
            i++;
            java.util.ArrayList<Class<?>> out = new java.util.ArrayList<>();
            while (methodDescriptor.charAt(i) != ')') {
                char c = methodDescriptor.charAt(i++);
                switch (c) {
                    case 'Z': out.add(boolean.class); break;
                    case 'B': out.add(byte.class); break;
                    case 'C': out.add(char.class); break;
                    case 'S': out.add(short.class); break;
                    case 'I': out.add(int.class); break;
                    case 'F': out.add(float.class); break;
                    case 'J': out.add(long.class); break;
                    case 'D': out.add(double.class); break;
                    case 'L': {
                        int semi = methodDescriptor.indexOf(';', i);
                        String cn = methodDescriptor.substring(i, semi).replace('/', '.');
                        out.add(Class.forName(cn, false, cl));
                        i = semi + 1;
                        break;
                    }
                    case '[': {
                        // parse array dimensions + component
                        int start = i - 1;
                        while (methodDescriptor.charAt(i) == '[') i++;
                        char t = methodDescriptor.charAt(i);
                        if (t == 'L') {
                            int semi = methodDescriptor.indexOf(';', i);
                            i = semi + 1;
                        } else {
                            i++; // primitive
                        }
                        String arrDesc = methodDescriptor.substring(start, i);
                        String jvmName = arrDesc.replace('/', '.');
                        // JVM name to Java name for arrays can be used directly with Class.forName
                        out.add(Class.forName(jvmName, false, cl));
                        break;
                    }
                    default: throw new IllegalArgumentException("Bad descriptor: " + methodDescriptor);
                }
            }
            return out.toArray(Class[]::new);
        }
    }
}
