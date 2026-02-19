package com.gwngames.game.input.mapper;

import com.gwngames.core.base.BaseTest;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.buffer.IInputCombo;
import com.gwngames.game.data.input.ComboPriority;
import com.gwngames.game.input.buffer.SmartInputBuffer;
import com.gwngames.game.input.controls.KeyInputIdentifier;
import org.junit.jupiter.api.Assertions;

import static com.badlogic.gdx.Input.Keys;

import java.util.Set;

/**
 * Verifies TTL-expiry + capacity logic of {@link SmartInputBuffer}.
 * Scenario
 * ────────────────────────────────────────────────────────────────────
 * • Buffer capacity = 3 combos.
 * • Each combo has TTL = 2 frames.
 * • We push one combo per frame for 3 frames.
 *   On frame 2, the first combo expires, leaving only 2 entries.
 * • Capacity guard therefore does **not** trim anything.
 */
public class InputBufferTest extends BaseTest {

    /* dummy identifiers */
    private static final IInputIdentifier A = new KeyInputIdentifier(Keys.A, true);
    private static final IInputIdentifier B = new KeyInputIdentifier(Keys.B, true);
    private static final IInputIdentifier C = new KeyInputIdentifier(Keys.C, true);

    /* helper that creates a TTL-2 combo around one identifier */
    private static IInputCombo mk(String name, IInputIdentifier id){
        return new IInputCombo() {
            @Override
            public int getMultId() {
                return 0;
            }

            @Override public String name()                       { return name; }
            @Override public Set<IInputIdentifier> identifiers() { return Set.of(id); }
            @Override public int  activeFrames()                 { return 2; }      // TTL = 2
            @Override public ComboPriority priority()            { return ComboPriority.NORMAL; }
        };
    }

    @Override
    protected void runTest() {

        SmartInputBuffer buf = new SmartInputBuffer(3);

        /* frame 0 – push first combo */
        buf.nextFrame(0, java.util.List.of(mk("A", A)));
        Assertions.assertEquals(1, buf.size());

        /* frame 1 – push second combo */
        buf.nextFrame(1, java.util.List.of(mk("B", B)));
        Assertions.assertEquals(2, buf.size());

        /* frame 2 – push third combo
           → first combo (“A”) expires before capacity trimming                */
        buf.nextFrame(2, java.util.List.of(mk("C", C)));

        /* size is 2 (B, C) because “A” aged out, capacity guard untouched */
        Assertions.assertEquals(2, buf.size(), "ring trimmed to capacity");
    }
}
