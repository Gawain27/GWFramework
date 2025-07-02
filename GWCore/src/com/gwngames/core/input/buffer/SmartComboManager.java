package com.gwngames.core.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.api.input.buffer.IInputComboManager;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

import java.util.*;

@Init(module = ModuleNames.CORE)
public final class SmartComboManager extends BaseComponent implements IInputComboManager {
    private final Map<Set<IInputIdentifier>,IInputCombo> bySet = new HashMap<>();

    public void register(IInputCombo c){ bySet.put(c.identifiers(), c); }

    @Override
    public List<IInputCombo> resolve(Set<IInputIdentifier> pressed) {

        /* collect every registered combo that is a subset of “pressed” */
        List<IInputCombo> candidates = bySet.entrySet().stream()
            .filter(e -> pressed.containsAll(e.getKey()))
            .map(Map.Entry::getValue)
            .toList();

        /* sort: priority ↓ then size(desc) ↓                          */
        candidates = new ArrayList<>(candidates);
        candidates.sort(java.util.Comparator
            .comparing(IInputCombo::priority).reversed()            // HIGH first
            .thenComparing((IInputCombo c)->c.identifiers().size())  // larger set first
            .reversed());

        /* greedy pick: consume identifiers as we go                   */
        Set<IInputIdentifier> remaining = new HashSet<>(pressed);
        List<IInputCombo> out = new ArrayList<>();

        for (IInputCombo c : candidates){
            if (remaining.containsAll(c.identifiers())){
                out.add(c);
                remaining.removeAll(c.identifiers());
            }
        }
        return out;
    }
}

