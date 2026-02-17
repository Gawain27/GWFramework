package com.gwngames.game.input.action;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputHistory;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Init(module = ModuleNames.CORE)
public class InputHistory extends BaseComponent implements IInputHistory {

    private final Map<IInputIdentifier, Long> idHits     = new ConcurrentHashMap<>();
    private final Map<IInputIdentifier, Long> idReleases = new ConcurrentHashMap<>();
    private final Map<IInputCombo, Long>      comboHits  = new ConcurrentHashMap<>();
    private final Map<IInputChain, Long>      chainHits  = new ConcurrentHashMap<>();

    // Optional: lightweight axis/touch counters
    private final Map<IInputIdentifier, Long> axisSamples = new ConcurrentHashMap<>();
    private final Map<IInputIdentifier, Long> touchDowns  = new ConcurrentHashMap<>();
    private final Map<IInputIdentifier, Long> touchDrags  = new ConcurrentHashMap<>();
    private final Map<IInputIdentifier, Long> touchUps    = new ConcurrentHashMap<>();

    @Override public void record (IInputIdentifier id){ idHits.merge(id, 1L, Long::sum); }
    @Override public void release(IInputIdentifier id){ idReleases.merge(id, 1L, Long::sum); }
    @Override public void record (IInputCombo combo){ comboHits.merge(combo, 1L, Long::sum); }
    @Override public void record (IInputChain chain){ chainHits.merge(chain, 1L, Long::sum); }

    @Override public Map<IInputIdentifier,Long> identifiers(){ return Map.copyOf(idHits); }
    @Override public Map<IInputIdentifier,Long> releases()   { return Map.copyOf(idReleases); }
    @Override public Map<IInputCombo,Long>      combos()     { return Map.copyOf(comboHits); }
    @Override public Map<IInputChain,Long>      chains()     { return Map.copyOf(chainHits); }

    /* NEW: simple counters; expand to store last (x,y,pressure) if needed */
    @Override public void axis     (IInputIdentifier id, float raw, float normalized){ axisSamples.merge(id, 1L, Long::sum); }
    @Override public void touchDown(IInputIdentifier id, float x, float y, float p) { touchDowns.merge(id, 1L, Long::sum); }
    @Override public void touchDrag(IInputIdentifier id, float x, float y, float p) { touchDrags.merge(id, 1L, Long::sum); }
    @Override public void touchUp  (IInputIdentifier id, float x, float y, float p) { touchUps.merge(id, 1L, Long::sum); }

    @Override public void clear(){
        idHits.clear(); idReleases.clear();
        comboHits.clear(); chainHits.clear();
        axisSamples.clear(); touchDowns.clear(); touchDrags.clear(); touchUps.clear();
    }
}

