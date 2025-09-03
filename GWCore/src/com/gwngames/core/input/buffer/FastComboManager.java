package com.gwngames.core.input.buffer;

import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.api.input.buffer.IInputComboManager;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.input.ComboDefinition;
import com.gwngames.core.data.input.InputParameters;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Init(module = ModuleNames.CORE)
public final class FastComboManager extends BaseComponent implements IInputComboManager {
    private static final FileLogger log = FileLogger.get(LogFiles.INPUT);
    @Inject
    IConfig cfg;

    private final Map<Set<IInputIdentifier>, IInputCombo> bySet = new HashMap<>();
    private final Map<IInputIdentifier, Set<IInputCombo>> inverted = new ConcurrentHashMap<>();

    @PostInject
    void applyDefaultTtl() {
        int frames = cfg.get(InputParameters.COMBO_DEFAULT_TTL_FRAMES);
        ComboDefinition.setDefaultTtlFrames(frames);
        log.info("Combo default TTL set to {} frames", frames);
    }

    @Override
    public synchronized void register(IInputCombo c) {
        bySet.put(c.identifiers(), c);
        for (IInputIdentifier id : c.identifiers()) {
            inverted.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(c);
        }
    }

    @Override
    public List<IInputCombo> resolve(Set<IInputIdentifier> pressed) {
        if (pressed.isEmpty()) return List.of();

        Set<IInputCombo> candidates = pressed.stream()
            .map(inverted::get)
            .filter(Objects::nonNull)
            .flatMap(Set::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (candidates.isEmpty()) return List.of();

        List<IInputCombo> filtered = candidates.stream()
            .filter(c -> pressed.containsAll(c.identifiers()))
            .collect(Collectors.toCollection(ArrayList::new));

        if (filtered.isEmpty()) return List.of();

        filtered.sort(
            Comparator.comparing(IInputCombo::priority).reversed()
                .thenComparingInt((IInputCombo c) -> c.identifiers().size()).reversed()
        );

        Set<IInputIdentifier> remaining = new HashSet<>(pressed);
        List<IInputCombo> out = new ArrayList<>(filtered.size());
        for (IInputCombo c : filtered) {
            if (remaining.containsAll(c.identifiers())) {
                out.add(c);
                remaining.removeAll(c.identifiers());
            }
        }
        return out;
    }
}
