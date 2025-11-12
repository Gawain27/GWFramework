package com.gw.map.io;

public interface TextureResolver {
    /**
     * Return a URL string suitable for new Image(url), or null if not resolvable.
     * Implementations may do file-system lookup or classpath resource loading, etc.
     */
    String resolve(String logicalPath) throws Exception;
}
