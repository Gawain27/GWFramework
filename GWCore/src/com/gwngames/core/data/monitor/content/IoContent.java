package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.*;
import java.util.concurrent.*;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.DASHBOARD_IO_CONTENT)
public final class IoContent extends BaseComponent implements IDashboardContent {

    // Snapshot stores list
    private static final List<FileStore> STORES;
    static {
        List<FileStore> tmp = new ArrayList<>();
        for (FileStore fs : FileSystems.getDefault().getFileStores()) tmp.add(fs);
        STORES = Collections.unmodifiableList(tmp);
    }

    private static final int WINDOW = 60;

    private final Deque<Double> readMBps  = new ArrayDeque<>(WINDOW);
    private final Deque<Double> writeMBps = new ArrayDeque<>(WINDOW);

    private volatile long lastRead  = 0;
    private volatile long lastWrite = 0;
    private volatile long lastNanos = System.nanoTime();

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Dash-IO");
        t.setDaemon(true);
        return t;
    });

    public IoContent() {
        exec.scheduleAtFixedRate(() -> {
            try { sample(); } catch (Throwable ignored) { }
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override public String templateId() { return "panel-kv-dualline"; }

    @Override
    public Object model() {
        long[] rw = totals();
        long rTot = rw[0], wTot = rw[1];

        double curR = peek(readMBps);
        double curW = peek(writeMBps);

        Map<String, Object> kv = new LinkedHashMap<>();
        kv.put("Read total",  humanMB(rTot));
        kv.put("Write total", humanMB(wTot));
        kv.put("Read now",    String.format(Locale.US, "%.2f MB/s", curR));
        kv.put("Write now",   String.format(Locale.US, "%.2f MB/s", curW));

        synchronized (this) {
            return Map.of(
                "kv", kv,
                "a",  List.copyOf(writeMBps),
                "b",  List.copyOf(readMBps),
                "alabel", "Write MB/s",
                "blabel", "Read MB/s"
            );
        }
    }

    /* ── internals ─────────────────────────────────────────────── */

    private void sample() {
        long[] rw = totals();
        long now  = System.nanoTime();
        double dt = Math.max(1e-6, (now - lastNanos) / 1_000_000_000.0); // seconds

        long dR = Math.max(0, rw[0] - lastRead);
        long dW = Math.max(0, rw[1] - lastWrite);

        double rMBs = (dR / 1_048_576.0) / dt;
        double wMBs = (dW / 1_048_576.0) / dt;

        synchronized (this) {
            push(readMBps,  rMBs);
            push(writeMBps, wMBs);
        }

        lastRead  = rw[0];
        lastWrite = rw[1];
        lastNanos = now;
    }

    /** Returns [readBytesTotal, writeBytesTotal] across all stores. */
    private static long[] totals() {
        long r = 0, w = 0;
        for (FileStore fs : STORES) {
            r += getLongAttr(fs, "totalReadBytes");
            w += getLongAttr(fs, "totalWriteBytes");
        }
        return new long[]{ r, w };
    }

    private static long getLongAttr(FileStore fs, String name) {
        try {
            Object v = fs.getAttribute(name);
            return (v instanceof Long) ? (Long) v : 0L;
        } catch (UnsupportedOperationException ignored) {
            return 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static void push(Deque<Double> dq, double v) {
        if (dq.size() == WINDOW) dq.removeFirst();
        dq.addLast(round2(v));
    }

    private static double round2(double d) { return Math.round(d * 100.0) / 100.0; }
    private static String humanMB(long bytes){
        double mb = bytes / 1_048_576.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
    private static double peek(Deque<Double> dq) { Double v = dq.peekLast(); return v == null ? 0.0 : v; }
}
