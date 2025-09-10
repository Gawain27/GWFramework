package com.gwngames.core.input.mapper;

import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.buffer.IInputComboManager;
import com.gwngames.core.data.input.ComboPriority;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.input.buffer.FastComboManager;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Verifies that SmartComboManager:
 *   • prefers higher priority combos
 *   • prefers larger combos when priority ties
 *   • never returns overlapping combos
 */
public final class InputComboManagerTest extends BaseTest {

    /* ────────────────────── identifiers used in combos ───────────── */
    private static final IInputIdentifier LEFT  = new DummyId("LEFT");
    private static final IInputIdentifier DOWN  = new DummyId("DOWN");
    private static final IInputIdentifier A     = new DummyId("A");
    private static final IInputIdentifier B     = new DummyId("B");

    /* ────────────────────── test body  ────────────────────────────── */
    @Override protected void runTest() {

        /*  build manager + combos */
        IInputComboManager mgr = new FastComboManager();

        IInputCombo downLeft = combo("DOWN_LEFT", 6, ComboPriority.HIGH , DOWN, LEFT);
        IInputCombo down     = combo("DOWN"     , 6, ComboPriority.NORMAL, DOWN);
        IInputCombo left     = combo("LEFT"     , 6, ComboPriority.NORMAL, LEFT);
        IInputCombo aOnly    = combo("A"        , 6, ComboPriority.NORMAL, A);
        IInputCombo bOnly    = combo("B"        , 6, ComboPriority.NORMAL, B);
        IInputCombo ab       = combo("AB"       , 6, ComboPriority.NORMAL, A, B);

        mgr.register(downLeft);
        mgr.register(down);
        mgr.register(left);
        mgr.register(aOnly);
        mgr.register(bOnly);
        mgr.register(ab);

        /*  press LEFT + DOWN + A + B in same frame */
        Set<IInputIdentifier> pressed = Set.of(LEFT, DOWN, A, B);

        List<IInputCombo> out = mgr.resolve(pressed);

        /*  expectations */
        Assertions.assertEquals(2, out.size(), "must resolve exactly 2 combos");
        Assertions.assertSame(downLeft, out.get(0), "down+left should win first");
        Assertions.assertSame(ab      , out.get(1), "AB should be second");
    }

    /* ───────────────────────── helpers ────────────────────────────── */

    /** Lightweight identifier just for this test */
    private record DummyId(String name) implements IInputIdentifier {
        @Override public String getDeviceType()    { return "keyboard"; }
        @Override public String getComponentType() { return "key"; }
        @Override public String getDisplayName()   { return name; }
        @Override public boolean isRecordWhilePressed() { return true; }

        @Override
        public void setRecordWhilePressed(boolean isRecordWhilePressed) {

        }

        @Override public int getMultId() { return 0; }
        @Override public String toString() { return name; }
        @Override public int hashCode() { return name.hashCode(); }
    }

    /** Factory for simple immutable combos used in the test */
    private static IInputCombo combo(String name,
                                     int ttl,
                                     ComboPriority prio,
                                     IInputIdentifier... ids){
        Set<IInputIdentifier> set = Set.of(ids);
        return new IInputCombo() {
            @Override
            public int getMultId() {
                return 0;
            }

            @Override public String            name()          { return name; }
            @Override public Set<IInputIdentifier> identifiers(){ return set; }
            @Override public int               activeFrames()  { return ttl;  }
            @Override public ComboPriority     priority()      { return prio; }
        };
    }
}
