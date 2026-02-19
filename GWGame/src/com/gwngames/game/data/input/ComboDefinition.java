package com.gwngames.game.data.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.buffer.IInputCombo;
import com.gwngames.game.api.input.buffer.IInputComboManager;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catalogue of simultaneous-press combos.
 * - If constructed with (ids, prio) → uses a dynamic default TTL loaded from config.
 * - If constructed with (ids, ttl, prio) → uses that explicit ttl and ignores the default.
 */
@Init(module = GameModule.GAME)
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
     private static volatile int DEFAULT_TTL_DYNAMIC = 8;
    /* ------------------------------------------------------------------ */

    private final Set<IInputIdentifier> ids;
    private final int           ttl;            // meaningful only if explicitTtl==true
    private final ComboPriority prio;
    private final boolean       explicitTtl;

    /* uses dynamic default TTL */
    ComboDefinition(Set<IInputIdentifier> ids, ComboPriority prio) {
        this.ids  = ids;
        this.ttl  = 0;                // ignored
        this.prio = prio;
        this.explicitTtl = false;
    }
    /* explicit ttl */
    ComboDefinition(Set<IInputIdentifier> ids, int ttl, ComboPriority prio) {
        this.ids  = ids;
        this.ttl  = ttl;
        this.prio = prio;
        this.explicitTtl = true;
    }

    /* ===== helper to shorten enum lines ===== */
    private static Set<IInputIdentifier> ids(String... names) {
        return Arrays.stream(names)
            .flatMap(n -> IdentifierDefinition.valueOf(n).ids().stream())
            .collect(Collectors.toCollection(LinkedHashSet::new)); // keeps order, dedups
    }

    /* ===== IInputCombo implementation ===== */
    @Override public Set<IInputIdentifier> identifiers() { return ids; }
    @Override public int  activeFrames()                  { return explicitTtl ? ttl : DEFAULT_TTL_DYNAMIC; }
    @Override public ComboPriority priority()             { return prio; }

    /* bulk-registration helper */
    public static void registerAll(IInputComboManager mgr) {
        for (ComboDefinition c : values()) mgr.register(c);
    }

    /** Called at boot by config loader to set global default TTL. */
    public static void setDefaultTtlFrames(int frames) {
        if (frames <= 0) throw new IllegalArgumentException("default TTL must be > 0");
        DEFAULT_TTL_DYNAMIC = frames;
    }
}
