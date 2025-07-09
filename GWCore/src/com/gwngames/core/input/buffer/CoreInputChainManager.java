package com.gwngames.core.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputChainManager;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.data.InputContext;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

import java.util.*;

@Init(module = ModuleNames.CORE)
public class CoreInputChainManager extends BaseComponent implements IInputChainManager {
    private record Rec(IInputChain chain, boolean enabled){}

    private final Map<String,Rec> byName = new HashMap<>();

    @Override
    public void register(IInputChain c, boolean enabled){ byName.put(c.name(), new Rec(c,enabled)); }
    @Override
    public void enable(String n){ byName.computeIfPresent(n,(k, r)->new Rec(r.chain,true )); }
    @Override
    public void disable(String n){ byName.computeIfPresent(n,(k, r)->new Rec(r.chain,false)); }

    /** longest-match algorithm (context-filtered) */
    @Override
    public Optional<IInputChain> match(List<? extends IInputCombo> buffer, InputContext ctx) {

        if (buffer.isEmpty()) return Optional.empty();

        for (int drop = 0; drop < buffer.size(); drop++) {
            var slice = buffer.subList(0, buffer.size() - drop);

            for (Rec rec : byName.values()) {
                if (!rec.enabled) continue;
                var vis = rec.chain.visibility();
                if (!vis.isEmpty() && !vis.contains(ctx)) continue;
                if (equalsSeq(slice, rec.chain.combos()))
                    return java.util.Optional.of(rec.chain());
            }
        }
        return java.util.Optional.empty();
    }

    private static boolean equalsSeq(List<? extends IInputCombo> buf, List<IInputCombo> chain){
        if (buf.size() < chain.size()) return false;
        for (int i=0;i<chain.size();i++)
            if (!buf.get(i).identifiers().equals(chain.get(i).identifiers()))
                return false;
        return true;
    }
}
