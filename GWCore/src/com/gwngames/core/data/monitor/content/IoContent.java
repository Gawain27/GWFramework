package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregated disk I/O counters (bytes read / written since boot)
 * for every mounted {@link FileStore}.
 *
 * <p>Relies on the JVM’s <code>FileStore#getAttribute("totalReadBytes")</code>
 * and <code>"totalWriteBytes"</code> support.  On platforms where the
 * attribute is missing the store is ignored, so the content degrades
 * gracefully.</p>
 */
public final class IoContent implements IDashboardContent {

    private static final List<FileStore> STORES =
        List.copyOf((java.util.Collection<? extends FileStore>) FileSystems.getDefault().getFileStores());

    @Override public String templateId() { return "kv"; }

    @Override public Object model() {
        long r = 0, w = 0;
        for (FileStore fs : STORES) {
            try {
                r += (Long) fs.getAttribute("totalReadBytes");
                w += (Long) fs.getAttribute("totalWriteBytes");
            } catch (UnsupportedOperationException ignored) { }
            catch (Exception e) { /* log if desired */ }
        }
        return Map.of(
            "Read",  human(r),
            "Write", human(w)
        );
    }

    private static String human(long bytes){
        double mb = bytes / 1_048_576.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
}
