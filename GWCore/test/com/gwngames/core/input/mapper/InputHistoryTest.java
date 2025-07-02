package com.gwngames.core.input.mapper;

import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.buffer.ComboPriority;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.api.input.buffer.InputContext;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.input.action.InputHistory;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies that {@link InputHistory} correctly counts identifiers, combos
 * and chains, and that {@link InputHistory#clear()} resets all counters.
 */
public final class InputHistoryTest extends BaseTest {

    /* ───────────────────────── identifiers used in test ───────────── */
    private static final IInputIdentifier X = new DummyId("X");
    private static final IInputIdentifier Y = new DummyId("Y");

    /* ───────────────────────── test body  ──────────────────────────── */
    @Override protected void runTest() {

        InputHistory h = new InputHistory();

        /*  identifiers */
        h.record(X); h.record(X); h.record(Y);

        Map<IInputIdentifier,Long> idMap = h.identifiers();
        Assertions.assertEquals(2L, idMap.get(X));
        Assertions.assertEquals(1L, idMap.get(Y));

        /*  combos */
        IInputCombo xy = combo("XY", X, Y);
        h.record(xy); h.record(xy); h.record(xy);

        Map<IInputCombo,Long> comboMap = h.combos();
        Assertions.assertEquals(3L, comboMap.get(xy));

        /*  chains */
        IInputChain chain = chain("CHAIN1", List.of(xy));
        h.record(chain);

        Map<IInputChain,Long> chainMap = h.chains();
        Assertions.assertEquals(1L, chainMap.get(chain));

        /*  clear */
        h.clear();
        Assertions.assertTrue(h.identifiers().isEmpty(), "identifiers cleared");
        Assertions.assertTrue(h.combos().isEmpty(),      "combos cleared");
        Assertions.assertTrue(h.chains().isEmpty(),      "chains cleared");
    }

    /* ───────────────────────── helpers ─────────────────────────────── */

    /** Lightweight identifier implementation just for this test. */
    private record DummyId(String name) implements IInputIdentifier {
        @Override public String toString(){ return name; }
        @Override public int hashCode()    { return name.hashCode(); }

        @Override
        public String getDeviceType() {
            return "";
        }

        @Override
        public String getComponentType() {
            return "";
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }

    /** Simple immutable combo for the test. */
    private static IInputCombo combo(String name, IInputIdentifier... ids){
        Set<IInputIdentifier> set = Set.of(ids);
        return new IInputCombo() {
            @Override public String name()                      { return name; }
            @Override public Set<IInputIdentifier> identifiers(){ return set;  }
            @Override public int  activeFrames()                { return 6;    }
            @Override public ComboPriority priority()           { return ComboPriority.NORMAL; }
        };
    }

    /** Simple immutable chain (no visibility logic needed for this test). */
    private static IInputChain chain(String name, List<IInputCombo> seq){
        return new IInputChain() {
            @Override public String            name()       { return name; }
            @Override public List<IInputCombo> combos()     { return seq; }
            @Override public Set<InputContext> visibility(){
                return java.util.Set.of();                  // visible everywhere
            }
        };
    }
}
