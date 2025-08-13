package com.gwngames.core.base;

import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.input.ChainDefinition;
import com.gwngames.core.data.input.ComboDefinition;
import org.junit.jupiter.api.Assertions;

/**
 * Enum mult-id behaviour.
 *   • Every constant inside the same enum must report a
 *     *different* id.                                 <── spec clarified
 *   • No two ids across *different* enum classes overlap.
 *   • Ids are stable across repeated calls.
 */
public final class EnumMultIdTest extends BaseTest {

    @Override
    protected void runTest() {

        /* ── prime the loader so it assigns mult-ids to enum constants ── */
        ModuleClassLoader.getInstance();   // walks enums, sets ids

        /* ── same-enum: ids must be different ─────────────────────────── */
        int downLeft = ComboDefinition.DOWN_LEFT.getMultId();
        int right    = ComboDefinition.RIGHT.getMultId();
        Assertions.assertNotEquals(
            downLeft, right,
            "Each ComboDefinition constant must have its own mult-id");

        int dash  = ChainDefinition.DASH.getMultId();
        int pause = ChainDefinition.PAUSE.getMultId();
        Assertions.assertNotEquals(
            dash, pause,
            "Each ChainDefinition constant must have its own mult-id");

        /* ── cross-enum: ids must never collide ───────────────────────── */
        Assertions.assertNotEquals(
            downLeft, dash,
            "mult-ids must be unique across different enum classes");

        /* ── stability check ───────────────────────────────────────────── */
        Assertions.assertEquals(
            downLeft, ComboDefinition.DOWN_LEFT.getMultId(),
            "mult-id must stay the same across repeated invocations");
    }
}
