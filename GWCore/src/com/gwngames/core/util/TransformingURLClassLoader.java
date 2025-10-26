package com.gwngames.core.util;

import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Set;

public class TransformingURLClassLoader extends URLClassLoader {
    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    private final IClassLoader gw;              // your ModuleClassLoader preferably
    private final ClosestWeaver weaver = new ClosestWeaver();

    // Only classes under these packages will be attempted child-first + weaving
    // Adjust to your modules (add project/game packages here)
    private final Set<String> childFirstPrefixes = Set.of(
        "com.gwngames"      // tighten if you prefer
    );

    public TransformingURLClassLoader(String name, URL[] urls, ClassLoader parent, IClassLoader gw) {
        super(name, urls, parent);
        this.gw = gw;
        LOG.debug("[XFormCL] INIT name=%s urls=%d", name, urls.length);
    }

    /* -------------------- CHILD-FIRST OVERRIDE -------------------- */
    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Only flip to child-first for packages we intend to weave
        if (isChildFirstCandidate(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            try {
                c = findClass(name);      // <-- run our transformer path
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {
                // fall through to parent if we can't find/define
            }
        }
        return super.loadClass(name, resolve);
    }

    private boolean isChildFirstCandidate(String className) {
        for (String p : childFirstPrefixes) {
            if (className.startsWith(p)) return true;
        }
        return false;
    }

    /* -------------------- TRANSFORMER -------------------- */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = getResourceAsStream(path)) {
            if (in == null) throw new ClassNotFoundException(name);
            byte[] bytes = in.readAllBytes();

            ClosestScan scan = ClosestScan.of(bytes);
            LOG.debug("[XFormCL] scan this=%s super=%s closest=%s hintedComp=%s hintedSub=%s initModule=%s initCompIfNotAuto=%s initSubIfNotAuto=%s",
                scan.thisInternalName(), scan.superInternalName(), scan.isClosest(),
                scan.hintComponentEnumSimple().orElse("-"),
                scan.hintSubEnumSimple().orElse("-"),
                scan.initModuleEnumSimple().orElse("-"),
                scan.initComponentEnumSimpleIfNotAuto().orElse("-"),
                scan.initSubEnumSimpleIfNotAuto().orElse("-"));

            if (scan.isClosest()) {
                // Resolve (component, sub, priority) WITHOUT loading the class itself
                String moduleSimple = scan.initModuleEnumSimple()
                    .orElseThrow(() -> new IllegalStateException("Missing @Init.module() on " + scan.thisInternalName()));
                int currentPrio = ModuleNames.valueOf(moduleSimple).getPriority();

                ComponentNames comp = scan.hintComponentEnumSimple()
                    .map(ComponentNames::valueOf)
                    .or(() -> scan.initComponentEnumSimpleIfNotAuto().map(ComponentNames::valueOf))
                    .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve component for " + scan.thisInternalName()
                            + " — set @ClosestOver(component=...) or non-AUTO @Init(component=...)"));

                SubComponentNames sub = scan.hintSubEnumSimple()
                    .map(SubComponentNames::valueOf)
                    .or(() -> scan.initSubEnumSimpleIfNotAuto().map(SubComponentNames::valueOf))
                    .orElse(SubComponentNames.NONE);

                LOG.debug("[XFormCL] deciding lower impl for comp=%s sub=%s prio=%d", comp, sub, currentPrio);

                Class<?> lower = gw.findNextLowerFor(comp, sub, currentPrio);

                String lowerInt = lower.getName().replace('.', '/');
                LOG.debug("[XFormCL] chosen lower = %s", lower.getName());

                // Validate ALL observed super-ctor descriptors
                List<String> ctors = scan.invokedSuperCtorDescs();
                if (ctors.isEmpty()) {
                    ensureCompatibleConstructor(lowerInt, "()V");
                    LOG.debug("[XFormCL] ctor check: default ()V OK");
                } else {
                    for (String d : ctors) {
                        ensureCompatibleConstructor(lowerInt, d);
                        LOG.debug("[XFormCL] ctor check: %s OK", d);
                    }
                }

                // Weave: rewrite superclass + INVOKESPECIAL(oldSuper -> lower)
                bytes = weaver.weave(bytes, lowerInt);
                LOG.debug("[XFormCL] weave complete: %s -> super=%s", name, lowerInt);
            } else {
                LOG.debug("[XFormCL] no weaving for %s (not Closest)", name);
            }

            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
            LOG.debug("[XFormCL] defineClass %s OK; super=%s", name,
                defined.getSuperclass() != null ? defined.getSuperclass().getName() : "null");
            return defined;
        } catch (IOException e) {
            LOG.error("[XFormCL] IO error loading %s", e, name);
            throw new ClassNotFoundException(name, e);
        } catch (Throwable t) {
            LOG.error("[XFormCL] transform/define failed for %s", t, name);
            throw new ClassNotFoundException("Failed to define " + name, t);
        }
    }

    /* -------------------- ctor-compat check (logs on error) -------------------- */
    private void ensureCompatibleConstructor(String chosenSuperInternal, String ctorDesc)
        throws Exception {
        String className = chosenSuperInternal.replace('/', '.');
        Class<?> superClass = Class.forName(className, false, getParent());
        Class<?>[] params = Descriptor.toParamTypes(ctorDesc, getParent());
        try {
            Constructor<?> c = superClass.getDeclaredConstructor(params);
            c.setAccessible(true);
        } catch (NoSuchMethodException e) {
            LOG.error("[XFormCL] chosen super %s lacks constructor %s", superClass.getName(), ctorDesc);
            throw e;
        }
    }

    /* -------------------- tiny descriptor helper -------------------- */
    static final class Descriptor {
        static Class<?>[] toParamTypes(String methodDescriptor, ClassLoader cl) throws ClassNotFoundException {
            int i = 0;
            if (methodDescriptor.charAt(i++) != '(') throw new IllegalArgumentException(methodDescriptor);
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
                        i = semi + 1; break;
                    }
                    case '[': {
                        int start = i - 1;
                        while (methodDescriptor.charAt(i) == '[') i++;
                        char t = methodDescriptor.charAt(i);
                        if (t == 'L') {
                            int semi = methodDescriptor.indexOf(';', i);
                            i = semi + 1;
                        } else i++;
                        String jvmName = methodDescriptor.substring(start, i).replace('/', '.');
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
