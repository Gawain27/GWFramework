package com.gwngames.core.input.action;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputHistory;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe counters for every identifier / combo / chain that passes
 * through a mapper.  No timing data – just totals.
 */
@Init(module = ModuleNames.CORE)
public class InputHistory extends BaseComponent implements IInputHistory {

    private final Map<IInputIdentifier, Long> idHits    = new ConcurrentHashMap<>();
    private final Map<IInputCombo, Long> comboHits = new ConcurrentHashMap<>();
    private final Map<IInputChain, Long> chainHits = new ConcurrentHashMap<>();

    /* ───────── increment helpers ───────── */
    @Override
    public void record(IInputIdentifier id){
        idHits.merge(id, 1L, Long::sum);
    }
    @Override
    public void record(IInputCombo combo){
        comboHits.merge(combo, 1L, Long::sum);
    }
    @Override
    public void record(IInputChain chain){
        chainHits.merge(chain, 1L, Long::sum);
    }

    /* ───────── read-only views ─────────── */
    @Override
    public Map<IInputIdentifier,Long> identifiers()
    { return Map.copyOf(idHits); }
    @Override
    public Map<IInputCombo,Long> combos()
    { return Map.copyOf(comboHits); }
    @Override
    public Map<IInputChain,Long> chains()
    { return Map.copyOf(chainHits); }

    /** Reset all counters (useful for tests or level restarts). */
    @Override
    public void clear(){
        idHits.clear(); comboHits.clear(); chainHits.clear();
    }
}
