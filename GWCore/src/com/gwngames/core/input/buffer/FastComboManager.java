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

/**
 * Resolves simultaneous-press combos in O(m log m) using:
 *  - an inverted index keyed by a semantic fingerprint (not object identity)
 *  - greedy selection by (priority desc, size desc, name asc) with no overlap.
 */
@Init(module = ModuleNames.CORE)
public final class FastComboManager extends BaseComponent implements IInputComboManager {
    private static final FileLogger log = FileLogger.get(LogFiles.INPUT);

    @Inject IConfig cfg;

    // Semantic fingerprint of an identifier (stable across instances)
    protected static final class Key {
        final String dev;   // e.g., "controller", "keyboard"
        final String comp;  // e.g., "button", "axis", "key"
        final int    mult;  // your custom grouping / player slot / device id
        final String disp;  // display name as last resort disambiguator

        Key(IInputIdentifier id) {
            this.dev  = n(id.getDeviceType());
            this.comp = n(id.getComponentType());
            this.mult = id.getMultId();
            this.disp = n(id.getDisplayName());
        }
        private static String n(String s){ return s == null ? "" : s; }

        @Override public boolean equals(Object o){
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            // Order of significance: dev, comp, mult, disp
            return mult == k.mult &&
                dev.equals(k.dev) &&
                comp.equals(k.comp) &&
                disp.equals(k.disp);
        }
        @Override public int hashCode(){
            int h = 17;
            h = 31*h + dev.hashCode();
            h = 31*h + comp.hashCode();
            h = 31*h + Integer.hashCode(mult);
            h = 31*h + disp.hashCode();
            return h;
        }
        @Override public String toString(){
            return dev + ":" + comp + ":" + mult + ":" + disp;
        }
    }

    // exact-set index → combo (kept, but not relied on for matching)
    private final Map<Set<IInputIdentifier>, IInputCombo> bySet = new HashMap<>();

    // Inverted index: semantic Key -> combos that include a matching identifier
    private final Map<Key, Set<IInputCombo>> inverted = new ConcurrentHashMap<>();

    // Precomputed semantic signature for each combo: Set<Key>
    private final Map<IInputCombo, Set<Key>> comboKeys = new ConcurrentHashMap<>();

    @PostInject
    void applyDefaultTtl() {
        int frames = cfg.get(InputParameters.COMBO_DEFAULT_TTL_FRAMES);
        ComboDefinition.setDefaultTtlFrames(frames);
        log.info("[combos] default TTL set to {} frames", frames);
    }

    @Override
    public synchronized void register(IInputCombo c) {
        bySet.put(c.identifiers(), c);

        // Precompute combo semantic keys
        Set<Key> keys = c.identifiers().stream().map(Key::new)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        comboKeys.put(c, Collections.unmodifiableSet(keys));

        // Populate semantic inverted index
        for (IInputIdentifier id : c.identifiers()) {
            inverted.computeIfAbsent(new Key(id), k -> ConcurrentHashMap.newKeySet()).add(c);
        }

        log.debug("[combos] register name={} size={} prio={} ids={}",
            c.name(), c.identifiers().size(), c.priority(), c.identifiers());
        log.debug("[combos] semantic keys for {} => {}", c.name(), keys);
    }

    @Override
    public List<IInputCombo> resolve(Set<IInputIdentifier> pressed) {
        if (pressed.isEmpty()) {
            log.debug("[combos] resolve: pressed=∅ ⇒ []");
            return List.of();
        }
        log.debug("[combos] resolve: pressed={}", pressed);

        // Normalize current pressed to semantic keys
        Set<Key> pressedKeys = pressed.stream().map(Key::new)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        log.debug("[combos] pressedKeys={}", pressedKeys);

        // Gather candidates by semantic inverted index
        Set<IInputCombo> candidates = pressed.stream()
            .map(Key::new)
            .map(inverted::get)
            .filter(Objects::nonNull)
            .flatMap(Set::stream)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        log.debug("[combos] candidates (by inverted) = {}", candidates.size());
        if (candidates.isEmpty()) return List.of();

        // Keep only combos whose semantic signature is included in pressedKeys
        List<IInputCombo> filtered = candidates.stream()
            .filter(c -> {
                Set<Key> keys = comboKeys.get(c);
                return keys != null && pressedKeys.containsAll(keys);
            })
            .collect(Collectors.toCollection(ArrayList::new));

        log.debug("[combos] filtered (subset of pressed) = {}", filtered.size());
        if (filtered.isEmpty()) return List.of();

        // Sort: priority DESC, then size DESC, then name ASC
        filtered.sort(
            Comparator.comparing(IInputCombo::priority).reversed()
                .thenComparingInt((IInputCombo c) -> c.identifiers().size()).reversed()
                .thenComparing(IInputCombo::name)
        );

        List<String> order = filtered.stream()
            .map(c -> c.name() + "(p=" + c.priority() + ",|ids|=" + c.identifiers().size() + ")")
            .toList();
        log.debug("[combos] sorted order = {}", order);

        // Greedy pick without overlapping semantic keys
        Set<Key> remaining = new HashSet<>(pressedKeys);
        List<IInputCombo> out = new ArrayList<>(filtered.size());
        for (IInputCombo c : filtered) {
            Set<Key> keys = comboKeys.get(c);
            if (keys != null && remaining.containsAll(keys)) {
                out.add(c);
                remaining.removeAll(keys);
                log.debug("[combos] pick name={} remainingKeys={}", c.name(), remaining.size());
            } else {
                log.debug("[combos] skip (overlap or not subset) name={}", c.name());
            }
        }

        log.debug("[combos] resolve result = {}", out.stream().map(IInputCombo::name).toList());
        return out;
    }
}
