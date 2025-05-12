package com.gwngames.core.base.cfg;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.ErrorPopupException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.PlatformNames;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Dynamically loads sub-project jars listed in {@code config.json},
 * orders them by {@code level} (high → low), and lets the game look up
 * components by annotation.
 */
public class ModuleClassLoader extends ClassLoader {

    /* ────────────────────────────  static  ─────────────────────────── */
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final List<Class<?>> buildComponents = new ArrayList<>();
    private static ModuleClassLoader instance;

    /* ────────────────────────────  instance  ───────────────────────── */
    private final List<URLClassLoader> orderedLoaders = new ArrayList<>();
    private final Map<URLClassLoader, JarFile> orderedJars = new LinkedHashMap<>();

    /* ==================================================================
     *  Life-cycle
     * =================================================================*/
    private ModuleClassLoader() throws ErrorPopupException {
        super(ModuleClassLoader.class.getClassLoader());
        initLoaders();
        initJars();
    }

    public static synchronized ModuleClassLoader getInstance() {
        if (instance == null) {
            try { instance = new ModuleClassLoader(); }
            catch (ErrorPopupException e) { throw new RuntimeException(e); }
        }
        return instance;
    }

    /* ==================================================================
     *  initLoaders
     * =================================================================*/
    @SuppressWarnings("unchecked")
    private void initLoaders() throws ErrorPopupException {
        log.debug("initLoaders() - locating bins & reading config...");

        /* 1) Where are we running from? -------------------------------- */
        File whereAmI;
        try {
            URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            whereAmI = new File(src.toURI());
            if (whereAmI.isFile()) whereAmI = whereAmI.getParentFile();
            log.debug("Executable dir: {}", whereAmI);
        } catch (Exception ex) {
            log.error("Error getting executable", ex);
            throw new ErrorPopupException("Cannot determine executable location");
        }

        /* 2) Locate bin directory -------------------------------------- */
        File binDir;
        if (new File(whereAmI, "config.json").isFile()) {
            binDir = whereAmI;
        } else if ("lib".equals(whereAmI.getName())) {
            binDir = new File(whereAmI.getParentFile(), "bin");
            if (!new File(binDir, "config.json").isFile())
                throw new ErrorPopupException("config.json not found beside launcher");
        } else {
            throw new ErrorPopupException("Cannot locate bin directory (looked in " + whereAmI + ")");
        }
        log.debug("binDir: {}", binDir);

        /* 3) Parse config.json ----------------------------------------- */
        File cfgFile = new File(binDir, "config.json");
        Map<String, Object> cfg;
        try (JsonReader reader = new JsonReader(new FileReader(cfgFile))) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            cfg = gson.fromJson(reader, mapType);
        } catch (IOException | JsonParseException e) {
            log.error("Failed to read config.json", e);
            throw new ErrorPopupException("Cannot read config.json");
        }

        String gameType = (String) cfg.get("gameType");
        List<Map<String,Object>> projects = (List<Map<String,Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty())
            throw new ErrorPopupException("No projects defined in config.json!");
        log.log("gameType: {}, projects: {}", gameType, projects.size());

        /* 4) Build classloaders --------------------------------------- */
        File rootDir = binDir.getParentFile();   // parent of bin & lib
        List<ProjectLoader> tmp = new ArrayList<>();

        for (Map<String,Object> p : projects) {
            String jarRel = (String) p.get("jar");
            Number lvl    = (Number) p.get("level");
            String type   = (String) p.get("type");

            log.debug("candidate  jar:{} level:{} type:{}", jarRel, lvl, type);

            if (!gameType.equals(type) || jarRel == null || lvl == null) {
                log.debug("skipped");
                continue;
            }

            File jarFile;
            try {
                jarFile = new File(rootDir, jarRel).getCanonicalFile();
            } catch (IOException e) {
                throw new ErrorPopupException("Could not lookup jar: " + jarRel);
            }
            if (!jarFile.exists()) {
                log.error("JAR not found: {}", jarFile);
                continue;
            }

            URLClassLoader cl;
            try {
                cl = createNamedLoader(jarFile);
            } catch (MalformedURLException e) {
                throw new ErrorPopupException("Cannot create class loader: " + jarFile.getName());
            }
            tmp.add(new ProjectLoader(cl, lvl.intValue()));
            log.log("loader '{}' (level {})", cl.getName(), lvl);
        }

        tmp.sort(Comparator.comparingInt(pl -> -pl.level));
        tmp.forEach(pl -> orderedLoaders.add(pl.classLoader));

        log.log("Loader order:");
        for (URLClassLoader l : orderedLoaders)
            log.log("{}  URLs:{}", l.getName(), Arrays.toString(l.getURLs()));
    }

    /** Create a URLClassLoader whose {@code getName()} is the jar file name. */
    private URLClassLoader createNamedLoader(File jarFile) throws MalformedURLException {
        URL url = jarFile.toURI().toURL();
        try {
            // Java 9+ constructor with name
            return URLClassLoader.class
                .getConstructor(String.class, URL[].class, ClassLoader.class)
                .newInstance(jarFile.getName(), new URL[]{url}, ModuleClassLoader.this);
        } catch (ReflectiveOperationException ignored) {
            // Java 8 fallback
            URLClassLoader cl = new URLClassLoader(new URL[]{url}, ModuleClassLoader.this);
            log.debug("Unnamed loader created for {}", jarFile.getName());
            return cl;
        }
    }

    /* ==================================================================
     *  initJars
     * =================================================================*/
    private void initJars() {
        log.debug("initJars() - scanning loaders for .jar files...");
        orderedLoaders.forEach(loader -> {
            for (URL url : loader.getURLs()) {
                File f = new File(url.getFile());
                if (!f.getName().endsWith(".jar")) continue;
                try {
                    JarFile jar = new JarFile(f);
                    orderedJars.put(loader, jar);
                    log.debug("{}  (loader '{}')", jar.getName(), loader.getName());
                } catch (IOException e) {
                    log.error("Error opening {}", f, e);
                }
            }
        });
    }

    /* ==================================================================
     *  Class-finding helpers
     * =================================================================*/
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        log.debug("findClass({})", name);
        for (URLClassLoader l : orderedLoaders) {
            try { return l.loadClass(name); }
            catch (ClassNotFoundException ignored) { }
        }
        throw new ClassNotFoundException("Class "+name+" not found in any module jar");
    }

    protected List<Class<?>> findClasses(ComponentNames name) throws ClassNotFoundException {
        log.debug("findClasses({})", name);
        List<Class<?>> result = new ArrayList<>();
        int foundPriority = 0;
        if (buildComponents.isEmpty()) buildComponents.addAll(getAnnotated(Init.class));

        for (Class<?> c : buildComponents) {
            Init ann = c.getAnnotation(Init.class);
            if (ann == null) continue;
            if (ann.component() == ComponentNames.NONE || ann.module() == ModuleNames.UNIMPLEMENTED)
                throw new IllegalStateException("Component not configured: " + c.getName());

            if (ann.component().equals(name)) {
                if (ann.module().modulePriority > foundPriority) {
                    result.clear();
                    foundPriority = ann.module().modulePriority;
                }
                result.add(c);
            }
        }
        if (result.isEmpty())
            throw new ClassNotFoundException("No implementation of "+name);
        return result;
    }

    protected Class<?> findClass(ComponentNames name) throws ClassNotFoundException {
        log.debug("findClass({})", name);
        Class<?> found = null;
        int priority = 0;
        ComponentNames foundComponent = null;
        if (buildComponents.isEmpty()) buildComponents.addAll(getAnnotated(Init.class));

        for (Class<?> c : buildComponents) {
            Init ann = c.getAnnotation(Init.class);
            if (ann == null) continue;
            if (ann.component()==ComponentNames.NONE || ann.module()==ModuleNames.UNIMPLEMENTED)
                throw new IllegalStateException("Component not configured: " + c.getName());

            if (ann.module().modulePriority==priority && !ann.allowMultiple()
                    && foundComponent != null && foundComponent.name().equals(ann.component().name()))
                throw new IllegalStateException("Multiple components configured: "+c.getName());

            if (ann.component().name().equals(name.name()) && ann.module().modulePriority > priority) {
                found = c;
                foundComponent = ann.component();
                priority = ann.module().modulePriority;
            }
        }
        if (found == null) throw new ClassNotFoundException("No implementation of "+name);
        return found;
    }

    /* ==================================================================
     *  Utility helpers
     * =================================================================*/
    public List<Class<?>> getAnnotated(Class<? extends Annotation> anno) {
        log.debug("getAnnotated({})", anno.getSimpleName());
        List<Class<?>> out = new ArrayList<>();
        orderedLoaders.forEach(loader -> {
            JarFile jar = orderedJars.get(loader);
            if (jar == null)
                throw new IllegalStateException("Null jar: " + loader.getName());
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                String cls = entry.getName().replace('/', '.').replace(".class", "");
                try {
                    Class<?> c = loader.loadClass(cls);
                    if (c.getAnnotation(anno) != null){
                        out.add(c);
                    };
                } catch (Throwable ignored) { }
            }
        });
        log.debug("found {} classes", out.size());
        return out;
    }

    public Object createInstance(Class<?> clazz, Object... params) {
        log.debug("createInstance({})", clazz.getSimpleName());
        try {
            Constructor<?> ctor;

            if (params.length == 0) {
                ctor = clazz.getDeclaredConstructor();
            } else {
                try {
                    Class<?>[] types = Arrays.stream(params)
                        .map(Object::getClass)
                        .toArray(Class<?>[]::new);
                    ctor = clazz.getDeclaredConstructor(types);
                } catch (NoSuchMethodException e) {
                    ctor = clazz.getDeclaredConstructor();
                    params = new Object[0];
                }
            }

            ctor.setAccessible(true);
            return ctor.newInstance(params);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instance of " + clazz.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T tryCreate(ComponentNames name, Object... params) {
        log.debug("tryCreate({}, ...)", name);
        try { return (T) createInstance(findClass(name), params); }
        catch (ClassNotFoundException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    public <T> T tryCreate(ComponentNames name, PlatformNames platform, Object... params) {
        log.debug("tryCreate({}, {}, ...)", name, platform);
        try {
            for (Class<?> c : findClasses(name)) {
                Init ann = c.getAnnotation(Init.class);
                if (ann.platform().equals(platform))
                    return (T) createInstance(c, params);
            }
        } catch (ClassNotFoundException ignored) { }
        throw new IllegalStateException("Cannot create component: " + name.name() + " - " + platform.name());
    }

    /* ================================================================== */
    private static class ProjectLoader {
        final URLClassLoader classLoader;
        final int level;
        ProjectLoader(URLClassLoader cl, int lvl) { classLoader = cl; level = lvl; }
    }
}
