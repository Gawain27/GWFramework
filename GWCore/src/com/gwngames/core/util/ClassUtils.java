package com.gwngames.core.util;

import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.cfg.ModuleClassLoader.ProjectLoader;
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
    public static List<Class<?>> getAnnotatedClasses(
        Class<? extends Annotation> anno) {
        return getAnnotatedClasses(anno, null);
    }

    /**
     * Return classes bearing the given annotation but **only** from module
     * JARs whose <em>level</em> equals {@code levelFilter}.  Pass
     * {@code null} for “all levels”.
     */
    public static List<Class<?>> getAnnotatedClasses(
        Class<? extends Annotation> anno, Integer levelFilter) {

        ModuleClassLoader mcl = ModuleClassLoader.getInstance();
        List<Class<?>> out = new ArrayList<>();

        for (ProjectLoader pl : mcl.getClassLoaders()) {
            if (levelFilter != null && pl.level() != levelFilter) continue;

            URLClassLoader cl = pl.cl();
            for (URL url : cl.getURLs()) {
                File f = new File(url.getFile());
                if (!f.getName().endsWith(".jar")) continue;

                try (JarFile jar = new JarFile(f)) {
                    Enumeration<JarEntry> e = jar.entries();
                    while (e.hasMoreElements()) {
                        JarEntry je = e.nextElement();
                        if (!je.getName().endsWith(".class")) continue;

                        String cn = je.getName()
                            .replace('/', '.')
                            .replace(".class", "");
                        try {
                            Class<?> c = Class.forName(cn, false, cl);
                            if (c.getAnnotation(anno) != null) out.add(c);
                        } catch (Throwable ignored) { /* class may not load */ }
                    }
                } catch (IOException ioe) {
                    log.error("Error reading {}", f, ioe);
                }
            }
        }
        log.debug("Found {} classes with @{}", out.size(), anno.getSimpleName());
        return out;
    }
}
