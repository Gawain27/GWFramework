package com.gwngames.core.api.build;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves commonly used filesystem paths for the running application.
 * All returned paths are absolute and normalized.
 */
@Init(component = ComponentNames.PATH_RESOLVER, module = ModuleNames.INTERFACE)
public interface IPathResolver extends IBaseComp {
    default Path assetsDir(){
     return execDir().getParent().resolve("data").resolve("assets").normalize();
    }

    /** Directory where the executable lives (jar parent, or classes/out/bin folder). */
    Path execDir();

    /** Directory the user launched the app from (System.getProperty("user.dir")). */
    Path workingDir();

    /** User home (~). */
    Path userHome();

    /** Temporary directory (java.io.tmpdir). */
    Path tempDir();

    /**
     * Per-OS base for user configuration/data:
     * <ul>
     *   <li>Windows: %APPDATA%</li>
     *   <li>macOS:   ~/Library/Application Support</li>
     *   <li>Linux:   $XDG_CONFIG_HOME or ~/.config</li>
     * </ul>
     */
    Path configBase();

    /* ---------------- convenience ---------------- */

    /** Resolve path relative to {@link #execDir()}. */
    Path resolveFromExec(String first, String... more);

    /** Resolve path relative to {@link #workingDir()}. */
    Path resolveFromWorking(String first, String... more);

    /** Normalize to absolute + resolve symlinks if possible. */
    Path normalize(Path p);

    /** True if child is inside parent (after normalization). */
    boolean isWithin(Path parent, Path child);
}
