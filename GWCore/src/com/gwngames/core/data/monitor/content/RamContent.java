package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Map;

/**
 * JVM heap and host RAM snapshot.
 *
 * <p>Uses the <kbd>kv</kbd> template &rarr; the renderer prints one
 * <code>key: value</code> line per map entry.</p>
 */
public final class RamContent implements IDashboardContent {

    private static final OperatingSystemMXBean OS =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final Runtime RT = Runtime.getRuntime();

    @Override public String templateId() { return "kv"; }

    @Override public Object model() {
        long hostTot = OS.getTotalMemorySize();
        long hostFree= OS.getFreeMemorySize();
        long jvmUsed = RT.totalMemory() - RT.freeMemory();
        long jvmMax  = RT.maxMemory();

        return Map.of(
            "JVM used",    human(jvmUsed),
            "JVM max",     human(jvmMax),
            "Host used",   human(hostTot - hostFree),
            "Host total",  human(hostTot)
        );
    }

    private static String human(long bytes){
        double mb = bytes / 1_048_576.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
}
