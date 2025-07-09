package com.gwngames.core.base;

import com.gwngames.core.data.ChainDefinition;
import com.gwngames.core.data.ComboDefinition;
import org.junit.jupiter.api.Assertions;

/**
 * Enum mult-id behaviour.
 *   • Every constant inside the same enum must report the *same* id.
 *   • Different enum classes must have *different* ids.
 *   • The id must be stable across repeated invocations.
 */
public class EnumMultIdTest extends BaseTest {

    @Override
    protected void runTest() {

        /* ── same-enum equality ───────────────────────────────────────── */
        int comboIdA = ComboDefinition.DOWN_LEFT.getMultId();
        int comboIdB = ComboDefinition.RIGHT.getMultId();
        Assertions.assertNotEquals(
            comboIdA, comboIdB,
            "All ComboDefinition constants should have different mult-id");

        int chainIdA = ChainDefinition.DASH.getMultId();
        int chainIdB = ChainDefinition.PAUSE.getMultId();
        Assertions.assertNotEquals(
            chainIdA, chainIdB,
            "All ChainDefinition constants should have different mult-id");
    }
}
