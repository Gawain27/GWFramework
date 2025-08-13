package com.gwngames.core.input.mapper;

import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.data.input.ComboPriority;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.data.input.InputContext;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.input.buffer.CoreInputChainManager;
import org.junit.jupiter.api.Assertions;

import java.util.*;

/**
 * Verifies that {@link CoreInputChainManager}:
 * <ul>
 *   <li>picks the first enabled chain whose combo-sequence matches the buffer</li>
 *   <li>respects visibility rules</li>
 *   <li>honours enable/disable switches</li>
 * </ul>
 */
public final class InputChainManagerTest extends BaseTest {

    /* ───── identifiers used through the test ─────────────────────── */
    private static final IInputIdentifier LEFT  = new Id("LEFT");
    private static final IInputIdentifier DOWN  = new Id("DOWN");
    private static final IInputIdentifier A     = new Id("A");
    private static final IInputIdentifier B     = new Id("B");

    /* ───── combos (immutable helpers) ─────────────────────────────── */
    private static final IInputCombo DOWN_LEFT = combo("DOWN_LEFT", DOWN, LEFT);
    private static final IInputCombo AB        = combo("AB",        A,    B);

    /* ───── chains --------------------------------------------------- */
    private static final IInputChain CHAIN_LONG =
        chain("LONG",  List.of(DOWN_LEFT, AB),
            Set.of(InputContext.GAMEPLAY));

    private static final IInputChain CHAIN_SHORT =
        chain("SHORT", List.of(DOWN_LEFT),
            Set.of(InputContext.MENU, InputContext.GAMEPLAY));

    /* ───── test implementation ────────────────────────────────────── */
    @Override protected void runTest() {

        CoreInputChainManager mgr = new CoreInputChainManager();

        /* register both chains, both enabled */
        mgr.register(CHAIN_LONG , true);
        mgr.register(CHAIN_SHORT, true);

        /* buffer simulates two combos pressed across frames 0 & 1 */
        List<IInputCombo> buffer = List.of(DOWN_LEFT, AB);

        /*   GAMEPLAY context: longest chain should match */
        var hit = mgr.match(buffer, InputContext.GAMEPLAY).orElse(null);
        Assertions.assertSame(CHAIN_LONG, hit, "should match LONG chain in gameplay");

        /*  MENU context: LONG invisible → expect SHORT */
        hit = mgr.match(buffer, InputContext.MENU).orElse(null);
        Assertions.assertSame(CHAIN_SHORT, hit, "should match SHORT chain in menu");

        /*  disable SHORT: now no chain visible in MENU */
        mgr.disable("SHORT");
        Assertions.assertTrue(mgr.match(buffer, InputContext.MENU).isEmpty(),
            "no match when only invisible/disabled chains remain");
    }

    /* ───────────────── helper types  ──────────────────────────────── */

    /** Lightweight identifier impl just for tests. */
    private record Id(String name) implements IInputIdentifier {
        @Override public String toString(){ return name; }
        @Override public int hashCode()   { return name.hashCode(); }

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

        @Override
        public boolean isRecordWhilePressed() {
            return true;
        }

        @Override
        public int getMultId() {
            return 0;
        }
    }

    /** Test-combo factory. */
    private static IInputCombo combo(String name, IInputIdentifier... ids){
        Set<IInputIdentifier> set = Set.of(ids);
        return new IInputCombo() {
            @Override
            public int getMultId() {
                return 0;
            }

            @Override public String name()           { return name; }
            @Override public Set<IInputIdentifier> identifiers(){ return set; }
            @Override public int  activeFrames()     { return 6; }
            @Override public ComboPriority priority(){ return ComboPriority.NORMAL; }
        };
    }

    /** Test-chain factory (no action binding). */
    private static IInputChain chain(String n,
                                     List<IInputCombo> seq,
                                     Set<InputContext> vis){
        List<IInputCombo> immutableSeq = List.copyOf(seq);
        Set<InputContext> immutableVis = Set.copyOf(vis);
        return new IInputChain() {
            @Override
            public int getMultId() {
                return 0;
            }

            @Override public String name()                  { return n; }
            @Override public List<IInputCombo> combos()     { return immutableSeq; }
            @Override public Set<InputContext> visibility() { return immutableVis; }
        };
    }
}
