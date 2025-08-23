package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Live CPU usage of <em>this JVM process</em> (graph over the last 60&nbsp;s).
 *
 * <p>Returns template&nbsp;<kbd>graph-line</kbd> and as its {@code model()}
 * a {@link List}&lt;Double&gt; of percentage values.  The default
 * {@code graph-line} template serialises that list into
 * <code>toString()</code> which yields a JSON-compatible array syntax
 * <code>[12.3, 15.7, …]</code>.</p>
 */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.DASHBOARD_CPU_CONTENT)
public class CpuContent extends BaseComponent implements IDashboardContent {

    private static final OperatingSystemMXBean OS =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    /** Sliding window – 60 samples ≈ last minute at 1 Hz. */
    private final Deque<Double> series = new ArrayDeque<>(60);

    @Override public String templateId() { return "graph-line"; }

    @Override public Object model() {
        double process = round(OS.getProcessCpuLoad() * 100);

        if (series.size() == 60) series.removeFirst();
        series.addLast(process);

        return List.copyOf(series);              // immutable snapshot
    }

    private static double round(double d) { return Math.round(d * 10) / 10.0; }
}
