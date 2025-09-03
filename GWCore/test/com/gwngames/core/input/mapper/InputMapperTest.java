package com.gwngames.core.input.mapper;

import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.api.input.action.IInputHistory;
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

    /* pick one concrete identifier from each logical definition */
    private static IInputIdentifier pick(IdentifierDefinition d) {
        return d.ids().iterator().next();
    }

    private static final IInputIdentifier DOWN = pick(IdentifierDefinition.DOWN);
    private static final IInputIdentifier LEFT = pick(IdentifierDefinition.LEFT);
    private static final IInputIdentifier  A   = pick(IdentifierDefinition.A);
    private static final IInputIdentifier  B   = pick(IdentifierDefinition.B);

    private static final AtomicInteger ACTION_CALLS = new AtomicInteger();
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    @Override protected void runTest() throws InterruptedException {

        setupApplication();   // LibGDX head-less scaffolding

        /* run-time dictionaries (no static singletons) */
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

        /* mapper wired with those helpers */
        TestMapper mapper = new TestMapper(comboMgr, chainMgr, buffer, dl_ab);

        /* ───── Frame 0 : DOWN+LEFT press & release ───── */
        mapper.onInput(new BtnEvt(DOWN, true));
        mapper.onInput(new BtnEvt(LEFT, true));
        mapper.onInput(new BtnEvt(DOWN, false));
        mapper.onInput(new BtnEvt(LEFT, false));
        mapper.endFrame();                 // buffer = [DOWN_LEFT]

        /* ───── Frame 1 : A+B ───── */
        mapper.onInput(new BtnEvt(A, true));
        mapper.onInput(new BtnEvt(B, true));
        mapper.endFrame();                 // buffer = [DOWN_LEFT , AB]

        /* ───── Frame 2 : idle (DL combo TTL expired) ───── */
        mapper.endFrame();                 // chain fires → DummyAction

        boolean ok = LATCH.await(200, TimeUnit.MILLISECONDS);
        Assertions.assertTrue(ok, "DummyAction did not fire within 200 ms");

        /* ── verifications ─────────────────────────────── */
        Assertions.assertEquals(1, ACTION_CALLS.get(), "DummyAction must fire exactly once");

        IInputHistory h = mapper.history();
        Assertions.assertEquals(1, h.combos().get(downLeft));
        Assertions.assertEquals(1, h.combos().get(ab));
        Assertions.assertEquals(1, h.chains().get(dl_ab));
        Assertions.assertEquals(1, h.identifiers().get(DOWN));   // recorded once (first press only)
    }

    /* mapper stub ------------------------------------------------------- */
    private static final class TestMapper extends BaseInputMapper {
        TestMapper(IInputComboManager cm, IInputChainManager chm, IInputBuffer buf, IInputChain chain) {
            this.comboMgr = cm;
            this.chainMgr = chm;
            this.buffer   = buf;
            this.history  = new InputHistory();
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
        }
    }

    /* tiny synthetic ButtonEvent */
    private static final class BtnEvt extends ButtonEvent {
        private final IInputIdentifier id; private final boolean pressed;
        BtnEvt(IInputIdentifier id, boolean pressed) {
            super(0, id, pressed, 1);
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
