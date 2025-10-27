package com.gwngames.core.util;

import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import groovyjarjarasm.asm.AnnotationVisitor;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static groovyjarjarasm.asm.Opcodes.ASM9;

/**
 * Miscellaneous reflection helpers used across GW Framework.
 */
public final class ClassUtils {

    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);

    private ClassUtils() { /* no-instantiation */ }

    /* ──────────────────────────────────────────────────────────────────────
     *  Field utilities (existing)
     * ─────────────────────────────────────────────────────────────────── */
    public static List<Field> getAnnotatedFields(Class<?> clazz,
                                                 Class<? extends Annotation> anno) {
        List<Field> out = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(anno)) out.add(f);
            }
            clazz = clazz.getSuperclass();
        }
        return out;
    }

    /**
     * Return every class on the game class-path that bears the given annotation.
     */
    public static List<Class<?>> getAnnotatedClasses(Class<? extends Annotation> anno) {
        // Use the loader’s full scan (jars + dirs + app classpath fallback)
        return ModuleClassLoader.getInstance().scanForAnnotated(anno);
    }

    public static boolean classfileHasAnnotation(InputStream in, String annDesc) throws IOException {
        final boolean[] hit = { false };
        new ClassReader(in).accept(
            new ClassVisitor(ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    if (annDesc.equals(desc)) hit[0] = true;
                    return super.visitAnnotation(desc, visible);
                }
            },
            ClassReader.SKIP_CODE
                | ClassReader.SKIP_DEBUG
                | ClassReader.SKIP_FRAMES
        );
        return hit[0];
    }

    /**
     * Return classes bearing the given annotation but **only** from module
     * JARs whose level equals {@code levelFilter}. If {@code levelFilter} is null,
     * delegate to the loader’s full scan.
     */
    public static List<Class<?>> getAnnotatedClasses(
        Class<? extends Annotation> anno, Integer levelFilter) {

        ModuleClassLoader mcl = ModuleClassLoader.getInstance();

        if (levelFilter == null) {
            // Full, robust scan (will find @Init in class directories during tests)
            List<Class<?>> found = mcl.scanForAnnotated(anno);
            log.debug("Found {} classes with @{}", found.size(), anno.getSimpleName());
            return found;
        }

        // ───────── Legacy filtered scan by project “level” (JARs only) ─────────
        List<Class<?>> out = new ArrayList<>();
        for (ModuleClassLoader.ProjectLoader pl : mcl.getClassLoaders()) {
            if (!Objects.equals(levelFilter, pl.level())) continue;

            URLClassLoader cl = pl.cl();
            for (URL url : cl.getURLs()) {
                File f = new File(url.getFile());
                if (!f.getName().endsWith(".jar")) continue;

                try (JarFile jar = new JarFile(f)) {
                    Enumeration<JarEntry> e = jar.entries();
                    while (e.hasMoreElements()) {
                        JarEntry je = e.nextElement();
                        if (!je.getName().endsWith(".class")) continue;
                        String cn = je.getName().replace('/', '.').replace(".class", "");
                        try {
                            Class<?> c = Class.forName(cn, false, cl);
                            if (c.getAnnotation(anno) != null) out.add(c);
                        } catch (Throwable ignored) { }
                    }
                } catch (IOException ioe) {
                    log.error("Error reading {}", f, ioe);
                }
            }
        }
        log.debug("Found {} classes with @{} at level {}", out.size(), anno.getSimpleName(), levelFilter);
        return out;
    }

    public static Class<?> extractGenericType(Field listField) {
        Type g = listField.getGenericType();
        if (!(g instanceof ParameterizedType pt)) {
            throw new IllegalStateException("Cannot resolve List element type for " + listField);
        }
        Type a = pt.getActualTypeArguments()[0];
        Class<?> raw = toRawClass(a);
        if (raw == null) {
            throw new IllegalStateException("Cannot resolve List element raw type for " + listField + " (arg=" + a + ")");
        }
        return raw;
    }

    public static Class<?> toRawClass(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType p) {
            Type raw = p.getRawType();
            return (raw instanceof Class<?> rc) ? rc : null;
        }
        if (t instanceof WildcardType w) {
            // Prefer upper bound if present, else lower bound.
            Type[] up = w.getUpperBounds();
            if (up.length > 0) {
                Class<?> c = toRawClass(up[0]);
                if (c != null) return c;
            }
            Type[] lo = w.getLowerBounds();
            if (lo.length > 0) {
                Class<?> c = toRawClass(lo[0]);
                if (c != null) return c;
            }
            return Object.class; // <?> falls back to Object
        }
        if (t instanceof TypeVariable<?> tv) {
            // First bound (or Object if none)
            Type[] b = tv.getBounds();
            return b.length == 0 ? Object.class : toRawClass(b[0]);
        }
        return null;
    }
}
