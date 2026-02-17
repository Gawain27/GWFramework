package com.gwngames.game.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.buffer.IInputBuffer;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;

import java.util.*;

/**
 * Size-bounded, auto-expiring ring of {@link IInputCombo}s for one adapter
 * slot.  Every stored element keeps the frame number when it was created.
 */
@Init(module = ModuleNames.CORE)
public class SmartInputBuffer extends BaseComponent implements IInputBuffer {

    /* ── logging ─────────────────────────────────────────────────── */
    private static final FileLogger log = FileLogger.get(LogFiles.INPUT);

    /* ── internal DTO (combo + frame stamp) ───────────────────────── */
    private record Timed(IInputCombo combo, long frame) { }


    /* ── state ────────────────────────────────────────────────────── */
    private final Deque<Timed> buf = new ArrayDeque<>();
    private final int max;

    public SmartInputBuffer(int maxCombos){
        if (maxCombos <= 0) throw new IllegalArgumentException("maxCombos must be > 0");
        this.max = maxCombos;
    }

    /* ====================================================================
       IInputBuffer
       ================================================================= */
    @Override
    public void nextFrame(long frame, List<IInputCombo> combos){
        /* add fresh combos */
        for (IInputCombo c : combos)
            buf.addLast(new Timed(c, frame));

        /* expire by TTL */
        buf.removeIf(t -> frame - t.frame >= t.combo.activeFrames());

        /* ring limit */
        while (buf.size() > max) buf.removeFirst();
    }

    @Override
    public List<IInputCombo> combos(){
        List<IInputCombo> out = new ArrayList<>(buf.size());
        for (Timed t : buf) out.add(t.combo);
        return List.copyOf(out);
    }

    @Override
    public void discard(int count){
        for (int i=0;i<count && !buf.isEmpty();i++) buf.removeFirst();
    }

    /* ====================================================================
       Extra helper for BaseInputMapper  – oldest element with timestamp
       ================================================================= */
    @Override
    public Optional<Entry> peekOldest(){
        Timed t = buf.peekFirst();
        return t == null ? Optional.empty()
            : Optional.of(new Entry(t.combo, t.frame));
    }

    /* ==================================================================== */
    public int  size()     { return buf.size(); }
    public int  capacity() { return max; }

    @Override public String toString(){
        StringBuilder sb = new StringBuilder("[");
        for (Timed t : buf) sb.append(t.combo.name()).append(',');
        if (!buf.isEmpty()) sb.setLength(sb.length()-1);
        return sb.append(']').toString();
    }
}
