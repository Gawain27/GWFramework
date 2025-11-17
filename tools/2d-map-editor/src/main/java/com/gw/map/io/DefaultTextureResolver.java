package com.gw.map.io;

import com.gw.map.ui.TemplateGalleryPane;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Default: try as absolute/relative file path; if not found, try classpath resource.
 */
public class DefaultTextureResolver implements TextureResolver {
    @Override
    public String resolve(String logicalPath) throws Exception {

        // 1) Absolute or relative filesystem path
        Path p = Path.of(logicalPath);
        if (!p.isAbsolute()) p = Path.of(".").resolve(logicalPath).normalize();
        if (Files.exists(p)) return p.toUri().toString();

        // 2) Classpath resource
        InputStream in = getClass().getClassLoader().getResourceAsStream(logicalPath.startsWith("/") ? logicalPath.substring(1) : logicalPath);
        if (in != null) {
            in.close();
            return Objects.requireNonNull(getClass().getClassLoader().getResource(logicalPath.startsWith("/") ? logicalPath.substring(1) : logicalPath)).toExternalForm();
        }
        return null;
    }
}
