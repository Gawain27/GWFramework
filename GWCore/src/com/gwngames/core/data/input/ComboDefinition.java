package com.gwngames.core.data.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.api.input.buffer.IInputComboManager;
import com.gwngames.core.data.ModuleNames;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catalogue of simultaneous-press combos.
 * <p>
 * ▸ Change {@link #DEFAULT_TTL} once – every 2-arg constructor inherits it.<br>
 * ▸ Two constructors:<br>
 * &nbsp;&nbsp;• <code>(ids, prio)</code> → uses {@link #DEFAULT_TTL}.<br>
 * &nbsp;&nbsp;• <code>(ids, ttl, prio)</code> → per-combo lifetime.
 * </p>
 */
@Init(module = ModuleNames.CORE)
public enum ComboDefinition implements IInputCombo {

    /* ─────── movement ─────── */
    DOWN_LEFT  (ids("DOWN", "LEFT"),  ComboPriority.HIGH),
    DOWN_RIGHT (ids("DOWN", "RIGHT"), ComboPriority.HIGH),
    UP         (ids("UP"),            ComboPriority.NORMAL),
    DOWN       (ids("DOWN"),          ComboPriority.NORMAL),
    LEFT       (ids("LEFT"),          ComboPriority.NORMAL),
    RIGHT      (ids("RIGHT"),         ComboPriority.NORMAL),

    /* ──── face buttons ────── */
    A_ONLY (ids("A"),       ComboPriority.NORMAL),
    B_ONLY (ids("B"),       ComboPriority.NORMAL),
    AB     (ids("A", "B"),  ComboPriority.NORMAL);

    /* ------------------------------------------------------------------ */
    public  static final int DEFAULT_TTL = 8;          // frames
    /* ------------------------------------------------------------------ */

    private final Set<IInputIdentifier> ids;
    private final int           ttl;
    private final ComboPriority prio;

    /* uses DEFAULT_TTL */
    ComboDefinition(Set<IInputIdentifier> ids, ComboPriority prio) {
        this(ids, DEFAULT_TTL, prio);
    }
    /* explicit ttl */
    ComboDefinition(Set<IInputIdentifier> ids, int ttl, ComboPriority prio) {
        this.ids  = ids;
        this.ttl  = ttl;
        this.prio = prio;
    }

    /* ===== helper to shorten enum lines ===== */
    private static Set<IInputIdentifier> ids(String... names) {
        return Arrays.stream(names)
            .flatMap(n -> IdentifierDefinition.valueOf(n).ids().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new)); // keeps order, dedups
    }

    /* ===== IInputCombo implementation ===== */
    @Override public Set<IInputIdentifier> identifiers() { return ids; }
    @Override public int  activeFrames()                  { return ttl; }
    @Override public ComboPriority priority()             { return prio; }

    /* bulk-registration helper */
    public static void registerAll(IInputComboManager mgr) {
        for (ComboDefinition c : values()) mgr.register(c);
    }

}
