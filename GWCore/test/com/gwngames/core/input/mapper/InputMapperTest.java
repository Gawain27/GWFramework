package com.gwngames.core.input.mapper;

import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.api.input.buffer.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.data.input.ComboPriority;
import com.gwngames.core.data.input.IdentifierDefinition;
import com.gwngames.core.data.input.InputContext;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.action.BaseInputMapper;
import com.gwngames.core.input.action.InputHistory;
import com.gwngames.core.input.buffer.FastComboManager;
import com.gwngames.core.input.buffer.FastInputChainManager;
import com.gwngames.core.input.buffer.SmartInputBuffer;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration-style test for {@link BaseInputMapper}.
 * Scenario:
 *   Frame 0 : press & release  DOWN+LEFT          → combo  DOWN_LEFT
 *   Frame 1 : press            A+B                → combo  AB
 *   Frame 2 : nothing, first combo expired        → chain  DL_AB fires DummyAction
 */
public final class InputMapperTest extends BaseTest {

    /* pick one concrete identifier from each logical definition — keyboard only */
    private static IInputIdentifier pick(IdentifierDefinition d) {
        // Prefer keyboard key ids to avoid mixed device families in the test
        return d.ids().stream()
            .filter(id -> {
                String dev = String.valueOf(id.getDeviceType()).toLowerCase(Locale.ROOT);
                String cmp = String.valueOf(id.getComponentType()).toLowerCase(Locale.ROOT);
                return dev.contains("key") || cmp.contains("key"); // “keyboard/key” style ids
            })
            .findFirst()
            // Fall back to the first one (keeps the test robust if naming changes)
            .orElseGet(() -> d.ids().iterator().next());
    }

    private static final IInputIdentifier DOWN = pick(IdentifierDefinition.DOWN);
    private static final IInputIdentifier LEFT = pick(IdentifierDefinition.LEFT);
    private static final IInputIdentifier  A   = pick(IdentifierDefinition.A);
    private static final IInputIdentifier  B   = pick(IdentifierDefinition.B);

    private static final AtomicInteger ACTION_CALLS = new AtomicInteger();
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    private static long t0 = 0;

    @Override
    protected void runTest() throws InterruptedException {

        setupApplication();

        t0 = System.nanoTime();

        IInputComboManager comboMgr  = new FastComboManager();
        IInputChainManager chainMgr  = new FastInputChainManager();
        IInputBuffer       buffer    = new SmartInputBuffer(8);

        IInputCombo downLeft = combo("DOWN_LEFT", DOWN, LEFT);
        IInputCombo ab       = combo("AB",        A,    B);

        comboMgr.register(downLeft);
        comboMgr.register(ab);

        IInputChain dl_ab = chain("DL_AB",
            List.of(downLeft, ab),
            Set.of(InputContext.GAMEPLAY));

        chainMgr.register(dl_ab, true);

        TestMapper mapper = new TestMapper(comboMgr, chainMgr, buffer, dl_ab);

        // Frame 0
        mapper.onInput(new BtnEvt(DOWN, true));
        mapper.onInput(new BtnEvt(LEFT, true));
        mapper.onInput(new BtnEvt(DOWN, false));
        mapper.onInput(new BtnEvt(LEFT, false));
        mapper.endFrame();
        log.debug("Δt after f0 = " + msSince(t0) + " ms");

        // Frame 1
        mapper.onInput(new BtnEvt(A, true));
        mapper.onInput(new BtnEvt(B, true));
        mapper.endFrame();
        log.debug("Δt after f1 = " + msSince(t0) + " ms");

        // Frame 2
        mapper.endFrame();
        log.debug("Δt after f2 = " + msSince(t0) + " ms");

        boolean ok = LATCH.await(300, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(ok, "DummyAction did not fire within 300 ms");
    }

    private static String msSince(long t0){
        return String.format(Locale.ROOT, "%.3f", (System.nanoTime() - t0)/1_000_000.0);
    }


    /* mapper stub ------------------------------------------------------- */
    private static final class TestMapper extends BaseInputMapper {
        TestMapper(IInputComboManager cm, IInputChainManager chm, IInputBuffer buf, IInputChain chain) {
            map(chain, Set.of(InputContext.GAMEPLAY), new DummyAction());
        }
        @Override public void map  (String c, IInputIdentifier id, IInputAction a) {}
        @Override public void unmap(String c, IInputIdentifier id)                 {}
        @Override public void clear(String c)                                      {}
    }

    private static final class DummyAction extends BaseComponent implements IInputAction {
        @Override public void execute(IInputEvent evt) {
            ACTION_CALLS.incrementAndGet();
            LATCH.countDown();
            InputMapperTest.log.debug("Δt latch execution = " + msSince(t0) + " ms");
        }
    }

    /* tiny synthetic ButtonEvent */
    private static final class BtnEvt extends ButtonEvent {
        private final IInputIdentifier id; private final boolean pressed;
        BtnEvt(IInputIdentifier id, boolean pressed) {
            this.id = id; this.pressed = pressed;
        }
        @Override public IInputIdentifier getControl() { return id;     }
        @Override public boolean          isPressed () { return pressed;}
    }

    /* helpers: anonymous combo / chain objects -------------------------- */
    private static IInputCombo combo(String n, IInputIdentifier... ids) {
        Set<IInputIdentifier> set = Set.of(ids);
        return new IInputCombo() {
            public String name()                        { return n;          }
            public Set<IInputIdentifier> identifiers()  { return set;        }
            public int  activeFrames()                  { return 2;          }
            public ComboPriority priority()             { return ComboPriority.NORMAL; }
            @Override public int getMultId()            { return 0; }
        };
    }

    private static IInputChain chain(String n, List<IInputCombo> seq, Set<InputContext> vis) {
        return new IInputChain() {
            public String name()                   { return n;   }
            public List<IInputCombo> combos()      { return seq; }
            public Set<InputContext> visibility()  { return vis; }
            @Override public int getMultId()       { return 0; }
        };
    }
}
