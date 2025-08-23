package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.DASHBOARD_RAM_CONTENT)
public final class RamContent extends BaseComponent implements IDashboardContent {

    private static final OperatingSystemMXBean OS =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final Runtime RT = Runtime.getRuntime();

    private static final int WINDOW = 60;

    /** JVM heap used as percent of max (0–100) over time. */
    private final Deque<Double> jvmPctSeries = new ArrayDeque<>(WINDOW);

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Dash-RAM");
        t.setDaemon(true);
        return t;
    });

    public RamContent() {
        exec.scheduleAtFixedRate(() -> {
            try { sample(); } catch (Throwable ignored) { }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override public String templateId() { return "panel-kv-line"; }

    @Override
    public Object model() {
        long hostTot  = OS.getTotalMemorySize();
        long hostFree = OS.getFreeMemorySize();
        long hostUsed = Math.max(0, hostTot - hostFree);

        long jvmUsed  = RT.totalMemory() - RT.freeMemory();
        long jvmMax   = RT.maxMemory();
        long jvmComm  = RT.totalMemory();

        Map<String, Object> kv = new LinkedHashMap<>();
        kv.put("JVM used",     humanMB(jvmUsed));
        kv.put("JVM committed",humanMB(jvmComm));
        kv.put("JVM max",      humanMB(jvmMax));
        kv.put("Host used",    humanMB(hostUsed));
        kv.put("Host total",   humanMB(hostTot));

        synchronized (this) {
            return Map.of(
                "kv", kv,
                "series", List.copyOf(jvmPctSeries),
                "label", "JVM heap %"
            );
        }
    }

    /* ── internals ─────────────────────────────────────────────── */

    private void sample() {
        long used = RT.totalMemory() - RT.freeMemory();
        long max  = Math.max(RT.maxMemory(), 1L);
        double pct = Math.min(100.0, Math.max(0.0, (used * 100.0) / max));
        synchronized (this) { push(jvmPctSeries, pct); }
    }

    private static void push(Deque<Double> dq, double v) {
        if (dq.size() == WINDOW) dq.removeFirst();
        dq.addLast(round1(v));
    }

    private static String humanMB(long bytes) {
        double mb = bytes / 1_048_576.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private static double round1(double d) { return Math.round(d * 10.0) / 10.0; }
}
