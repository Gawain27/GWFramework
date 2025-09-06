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

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.DASHBOARD_CPU_CONTENT)
public final class CpuContent extends BaseComponent implements IDashboardContent {

    private static final OperatingSystemMXBean OS =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    private static final int WINDOW = 60;

    private final Deque<Double> processSeries = new ArrayDeque<>(WINDOW);
    private final Deque<Double> systemSeries  = new ArrayDeque<>(WINDOW);

    public CpuContent() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Dash-CPU");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleAtFixedRate(() -> {
            try { sample(); } catch (Throwable ignored) { /* keep sampling */ }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override public String templateId() { return "panel-kv-dualline"; }

    @Override
    public Object model() {
        Map<String, Object> kv = new LinkedHashMap<>();
        double proc = clampPct(OS.getProcessCpuLoad() * 100.0);
        double sys  = clampPct(OS.getCpuLoad() * 100.0);
        int cores   = Runtime.getRuntime().availableProcessors();
        double load = Optional.of(ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage())
            .orElse(-1.0);

        kv.put("Process CPU", fmtPct(proc));
        kv.put("System CPU",  fmtPct(sys));
        kv.put("Cores",       cores);
        kv.put("Load avg (1m)", load >= 0 ? String.format(Locale.US, "%.2f", load) : "n/a");

        synchronized (this) {
            return Map.of(
                "kv", kv,
                "a",  List.copyOf(processSeries),
                "b",  List.copyOf(systemSeries),
                "alabel", "Process %",
                "blabel", "System %"
            );
        }
    }

    @Override
    public void setComponent(BaseComponent component) {
    }

    /* ── internals ─────────────────────────────────────────────── */

    private void sample() {
        double proc = clampPct(OS.getProcessCpuLoad() * 100.0);
        double sys  = clampPct(OS.getCpuLoad() * 100.0);
        synchronized (this) {
            push(processSeries, proc);
            push(systemSeries,  sys);
        }
    }

    private static void push(Deque<Double> dq, double v) {
        if (dq.size() == WINDOW) dq.removeFirst();
        dq.addLast(round1(v));
    }

    private static double clampPct(double v) {
        if (Double.isNaN(v) || v < 0) return 0.0;
        if (v > 100) return 100.0;
        return v;
    }

    private static double round1(double d) { return Math.round(d * 10.0) / 10.0; }
    private static String fmtPct(double d) { return String.format(Locale.US, "%.1f %%", d); }
}
