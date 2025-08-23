package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.DASHBOARD_IO_CONTENT)
public final class IoContent extends BaseComponent implements IDashboardContent {

    // Build an unmodifiable list from the Iterable (no Collection cast!)
    private static final List<FileStore> STORES;
    static {
        List<FileStore> tmp = new ArrayList<>();
        for (FileStore fs : FileSystems.getDefault().getFileStores()) {
            tmp.add(fs);
        }
        STORES = Collections.unmodifiableList(tmp);
    }

    @Override public String templateId() { return "kv"; }

    @Override public Object model() {
        long read = 0, write = 0;
        for (FileStore fs : STORES) {
            read  += getLongAttr(fs, "totalReadBytes");
            write += getLongAttr(fs, "totalWriteBytes");
        }
        return Map.of(
            "Read",  human(read),
            "Write", human(write)
        );
    }

    private static long getLongAttr(FileStore fs, String name) {
        try {
            Object v = fs.getAttribute(name);
            return (v instanceof Long) ? (Long) v : 0L;
        } catch (UnsupportedOperationException ignored) {
            return 0L; // attribute not supported on this FS
        } catch (Exception ignored) {
            return 0L; // be resilient
        }
    }

    private static String human(long bytes){
        double mb = bytes / 1_048_576.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
}
