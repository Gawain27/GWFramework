package com.gwngames.core.util;

import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ConcurrentHashMap;

public class TransformingURLClassLoader extends URLClassLoader {
    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);

    private final IClassLoader gw;
    private final ClosestWeaver weaver = new ClosestWeaver();

    // tiny cache to avoid re-reading the same class bytes
    private final ConcurrentHashMap<String, Boolean> closestCache = new java.util.concurrent.ConcurrentHashMap<>();

    public TransformingURLClassLoader(String name, URL[] urls, ClassLoader parent, IClassLoader gw) {
        super(name, urls, parent);
        LOG.debug("[XFormCL] INIT name=%s urls=%d", name, urls.length);
        this.gw = gw;
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Already defined by *this* loader?
        Class<?> c = findLoadedClass(name);
        if (c != null) { if (resolve) resolveClass(c); return c; }

        // If we don't even have the resource in this loader, do normal parent-first.
        String path = name.replace('.', '/') + ".class";
        URL localRes = findResource(path);
        if (localRes == null) {
            return super.loadClass(name, resolve);
        }

        // We have the bytes locally. Peek them to decide: Closest → child-first; otherwise parent-first.
        boolean isClosest = closestCache.computeIfAbsent(name, n -> {
            try (InputStream in = localRes.openStream()) {
                byte[] bytes = in.readAllBytes();
                return ClosestScan.of(bytes).isClosest();
            } catch (Throwable t) {
                // be conservative: if we can't decide, do parent-first
                LOG.debug("[XFormCL] peek failed for %s; delegating to parent", t, n);
                return false;
            }
        });

        if (!isClosest) {
            // Prefer parent for non-closest (APIs, data enums, etc.) → single class identity
            try {
                return super.loadClass(name, resolve);
            } catch (ClassNotFoundException ignore) {
                // parent doesn't know it, define here
            }
        }

        // Child-first for Closest (needs weaving), or fallback if parent didn't have it.
        try {
            Class<?> defined = findClass(name); // does weaving when needed
            if (resolve) resolveClass(defined);
            return defined;
        } catch (ClassNotFoundException e) {
            // Last resort: parent (rare edge if our local resource disappeared)
            return super.loadClass(name, resolve);
        }
    }

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
                // resolve comp/sub/prio without loading the class
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

                Class<?> lower = gw.findNextLowerFor(comp, sub, currentPrio);
                String lowerInt = lower.getName().replace('.', '/');

                // validate super-ctor signatures observed
                java.util.List<String> ctors = scan.invokedSuperCtorDescs();
                if (ctors.isEmpty()) ensureCompatibleConstructor(lowerInt, "()V");
                else for (String d : ctors) ensureCompatibleConstructor(lowerInt, d);

                // weave
                bytes = weaver.weave(bytes, lowerInt);
                LOG.debug("[XFormCL] weave complete: %s -> super=%s", name, lowerInt);
            } else {
                LOG.debug("[XFormCL] no weaving for %s (not Closest)", name);
            }

            Class<?> defined = defineClass(name, bytes, 0, bytes.length);
            LOG.debug("[XFormCL] defineClass %s OK; super=%s",
                name, defined.getSuperclass() != null ? defined.getSuperclass().getName() : "null");
            return defined;
        } catch (IOException e) {
            LOG.error("[XFormCL] IO error loading %s", e, name);
            throw new ClassNotFoundException(name, e);
        } catch (Throwable t) {
            LOG.error("[XFormCL] transform/define failed for %s", t, name);
            throw new ClassNotFoundException("Failed to define " + name, t);
        }
    }

    private void ensureCompatibleConstructor(String chosenSuperInternal, String ctorDesc) throws Exception {
        String className = chosenSuperInternal.replace('/', '.');
        Class<?> superClass = Class.forName(className, false, getParent());
        Class<?>[] params = Descriptor.toParamTypes(ctorDesc, getParent());
        superClass.getDeclaredConstructor(params).setAccessible(true);
    }

    static final class Descriptor {
        static Class<?>[] toParamTypes(String methodDescriptor, ClassLoader cl) throws ClassNotFoundException {
            int i = 0; if (methodDescriptor.charAt(i++) != '(') throw new IllegalArgumentException(methodDescriptor);
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
                    case 'L' -> { int semi = methodDescriptor.indexOf(';', i);
                        out.add(Class.forName(methodDescriptor.substring(i, semi).replace('/', '.'), false, cl));
                        i = semi + 1; }
                    case '[' -> { int start = i - 1; while (methodDescriptor.charAt(i) == '[') i++;
                        if (methodDescriptor.charAt(i) == 'L') i = methodDescriptor.indexOf(';', i) + 1; else i++;
                        out.add(Class.forName(methodDescriptor.substring(start, i).replace('/', '.'), false, cl)); }
                    default -> throw new IllegalArgumentException("Bad descriptor: " + methodDescriptor);
                }
            }
            return out.toArray(Class[]::new);
        }
    }
}
