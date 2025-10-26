package com.gwngames.core.util;

import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
}
