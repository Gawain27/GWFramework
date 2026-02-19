package com.gwngames.core.build;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.build.IPathResolver;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Locale;

/**
 * Default path resolver. Works in IDE and packaged JAR deployments.
 */
@Init(module = CoreModule.CORE)
public final class CorePathResolver extends BaseComponent implements IPathResolver {
    private final Path execDir;
    private final Path workingDir;
    private final Path userHome;
    private final Path tempDir;
    private final Path configBase;

    public CorePathResolver() {
        // --- working dir / simple system properties
        this.workingDir = absNorm(Paths.get(System.getProperty("user.dir", ".")));
        this.userHome   = absNorm(Paths.get(System.getProperty("user.home", ".")));
        this.tempDir    = absNorm(Paths.get(System.getProperty("java.io.tmpdir", ".")));

        // --- executable location (jar or classes folder)
        Path execBase;

        try {
            URL codeSrc = CorePathResolver.class.getProtectionDomain()
                .getCodeSource()
                .getLocation();
            Path loc = Paths.get(codeSrc.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(loc) && loc.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                execBase = loc.getParent() != null ? loc.getParent() : loc.getRoot();
            } else {
                // Running from classes/IDE – treat the directory itself as execDir
                execBase = Files.isDirectory(loc) ? loc : loc.getParent();
                if (execBase == null) execBase = workingDir; // extreme fallback
            }
        } catch (URISyntaxException e) {
            execBase = workingDir;
        }

        this.execDir = absNorm(execBase);

        // --- config base (per OS)
        this.configBase = absNorm(detectConfigBase(userHome));
    }

    // ---------------- IPathResolver ----------------

    @Override public Path execDir()            { return execDir; }
    @Override public Path workingDir()         { return workingDir; }
    @Override public Path userHome()           { return userHome; }
    @Override public Path tempDir()            { return tempDir; }
    @Override public Path configBase()         { return configBase; }

    @Override
    public Path resolveFromExec(String first, String... more) {
        return absNorm(execDir.resolve(Paths.get(first, more)));
    }

    @Override
    public Path resolveFromWorking(String first, String... more) {
        return absNorm(workingDir.resolve(Paths.get(first, more)));
    }

    @Override
    public Path normalize(Path p) {
        return absNorm(p);
    }

    @Override
    public boolean isWithin(Path parent, Path child) {
        Path p = absNorm(parent);
        Path c = absNorm(child);
        return c.startsWith(p);
    }

    // ---------------- helpers ----------------

    private static Path absNorm(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        try {
            // Try to resolve symlinks if permitted (keeps behavior stable across platforms)
            return abs.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (Exception ignored) {
            return abs;
        }
    }

    private static Path detectConfigBase(Path userHome) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData);
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData);
            }
            return userHome.resolve("AppData").resolve("Roaming");
        } else if (os.contains("mac")) {
            return userHome.resolve("Library").resolve("Application Support");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            if (xdg != null && !xdg.isBlank()) {
                return Paths.get(xdg);
            }
            return userHome.resolve(".config");
        }
    }
}
