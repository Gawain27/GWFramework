package com.gwngames.core.input.mapper;

import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.api.input.action.IInputHistory;
import com.gwngames.core.api.input.buffer.*;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.input.action.BaseInputMapper;
import com.gwngames.core.input.action.InputHistory;
import com.gwngames.core.input.buffer.CoreInputChainManager;
import com.gwngames.core.input.buffer.SmartComboManager;
import com.gwngames.core.input.buffer.SmartInputBuffer;
import com.gwngames.core.event.input.ButtonEvent;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class InputMapperTest extends BaseTest {

    /* identifiers */
    private static final IInputIdentifier DOWN = IdentifierDefinition.DOWN.id();
    private static final IInputIdentifier LEFT = IdentifierDefinition.LEFT.id();
    private static final IInputIdentifier A    = IdentifierDefinition.A.id();
    private static final IInputIdentifier B    = IdentifierDefinition.B.id();

    /* combos & chain */
    private static final IInputCombo DOWN_LEFT = combo("DOWN_LEFT", DOWN, LEFT);
    private static final IInputCombo AB        = combo("AB", A, B);

    private static final IInputChain DL_AB = chain("DL_AB",
        List.of(DOWN_LEFT, AB), Set.of(InputContext.GAMEPLAY));

    private static final SmartComboManager      COMBOS = new SmartComboManager();
    private static final CoreInputChainManager  CHAINS = new CoreInputChainManager();
    private static final SmartInputBuffer       BUFFER = new SmartInputBuffer(8);

    private static final AtomicInteger actionCalls = new AtomicInteger(0);

    @Override
    protected void runTest() {

        COMBOS.register(DOWN_LEFT);
        COMBOS.register(AB);
        CHAINS.register(DL_AB, true);

        TestMapper mapper = new TestMapper();

        /* frame 0: DOWN + LEFT */
        mapper.onInput(new BtnEvt(DOWN, true));
        mapper.onInput(new BtnEvt(LEFT, true));
        mapper.onInput(new BtnEvt(DOWN, false));
        mapper.onInput(new BtnEvt(LEFT, false));
        mapper.endFrame();                   // buffer [DOWN_LEFT]

        /* frame 1: A + B */
        mapper.onInput(new BtnEvt(A, true));
        mapper.onInput(new BtnEvt(B, true));
        mapper.endFrame();                   // buffer [DOWN_LEFT, AB]

        /* frame 2: advance with no input – chain resolves here */
        mapper.endFrame();

        /* assertions */
        Assertions.assertEquals(1, actionCalls.get(), "action executed once");

        IInputHistory h = mapper.history();
        Assertions.assertEquals(1, h.combos().get(DOWN_LEFT));
        Assertions.assertEquals(1, h.combos().get(AB));
        Assertions.assertEquals(1, h.chains().get(DL_AB));
        Assertions.assertEquals(1, h.identifiers().get(DOWN));
    }

    /* mapper */
    private static final class TestMapper extends BaseInputMapper {
        TestMapper() {
            this.comboMgr = COMBOS;
            this.chainMgr = CHAINS;
            this.buffer   = BUFFER;
            this.history  = new InputHistory();
            map(DL_AB, Set.of(InputContext.GAMEPLAY), new DummyAction());
        }
        @Override public void map  (String c, IInputIdentifier id, IInputAction a) {}
        @Override public void unmap(String c, IInputIdentifier id)                 {}
        @Override public void clear(String c)                                      {}
    }

    /* dummy action */
    private static class DummyAction implements IInputAction {
        @Override public void execute(IInputEvent event) { actionCalls.incrementAndGet(); }
    }

    /* synthetic ButtonEvent */
    private static class BtnEvt extends ButtonEvent {
        private final IInputIdentifier id; private final boolean pressed;
        BtnEvt(IInputIdentifier id, boolean pressed) {
            super(0, id, pressed, 1);
            this.id = id; this.pressed = pressed;
        }
        @Override public IInputIdentifier getControl() { return id; }
        @Override public boolean          isPressed()  { return pressed; }
    }

    /* helpers to build anonymous combos/chains */
    private static IInputCombo combo(String n, IInputIdentifier... ids) {
        Set<IInputIdentifier> set = Set.of(ids);
        return new IInputCombo() {
            public String name()                       { return n; }
            public Set<IInputIdentifier> identifiers() { return set; }
            public int  activeFrames()                 { return 2; }
            public ComboPriority priority()            { return ComboPriority.NORMAL; }
        };
    }
    private static IInputChain chain(String n, List<IInputCombo> seq, Set<InputContext> vis) {
        return new IInputChain() {
            public String name()                  { return n; }
            public List<IInputCombo> combos()     { return seq; }
            public Set<InputContext> visibility() { return vis; }
        };
    }
}
