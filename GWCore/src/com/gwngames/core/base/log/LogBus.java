package com.gwngames.core.base.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal per-component log index used by the dashboard. */
public final class LogBus {
    public enum Level { DEBUG, INFO, ERROR }

    public static final int MAX_BUFFER_PER_COMP = 500;

    private static final class Buf {
        final ArrayDeque<String> ring = new ArrayDeque<>(MAX_BUFFER_PER_COMP);
        final AtomicInteger errors = new AtomicInteger();
        synchronized void add(String line, boolean isError) {
            if (ring.size() == MAX_BUFFER_PER_COMP) ring.removeFirst();
            ring.addLast(line);
            if (isError) errors.incrementAndGet();
        }
        synchronized List<String> snapshot() { return new ArrayList<>(ring); }
    }

    private static final ConcurrentHashMap<String, Buf> BY_COMP = new ConcurrentHashMap<>();
    private static Buf buf(String key) { return BY_COMP.computeIfAbsent(key, k -> new Buf()); }

    public static void record(String compKey, Level lvl, String msg, Throwable ex) {
        String ts = Instant.now().toString();
        StringBuilder sb = new StringBuilder(256)
            .append(ts).append(" ").append(lvl).append(" ").append(compKey).append(" — ").append(msg);
        if (ex != null) {
            StringWriter sw = new StringWriter(2048);
            ex.printStackTrace(new PrintWriter(sw));
            sb.append("\n").append(sw);
        }
        boolean isErr = (lvl == Level.ERROR);
        buf(compKey).add(sb.toString(), isErr);
    }

    public static int errorCount(String compKey) {
        return buf(compKey).errors.get();
    }

    public static List<String> recent(String compKey) {
        List<String> r = buf(compKey).snapshot();
        return Collections.unmodifiableList(r);
    }
}
