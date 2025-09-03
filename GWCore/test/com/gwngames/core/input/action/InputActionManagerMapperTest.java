package com.gwngames.core.input.action;

import com.badlogic.gdx.Input;
import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.buffer.FastComboManager;
import com.gwngames.core.input.buffer.FastInputChainManager;
import com.gwngames.core.input.buffer.SmartInputBuffer;
import com.gwngames.core.input.controls.KeyInputIdentifier;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration test: InputActionManager.attachMapper / detachMapper.
 */
public class InputActionManagerMapperTest extends BaseTest {

    /* ───────────────────────── Stub classes ─────────────────────────── */

    /* counts every execution – no enable / cooldown logic */
    private static final class CounterAction extends BaseComponent implements IInputAction {
        private final AtomicInteger cnt = new AtomicInteger();
        @Override public void execute(IInputEvent ctx){ cnt.incrementAndGet(); }
        int hits(){ return cnt.get(); }
    }

    /**
     * Mini-mapper: fires {@link CounterAction} whenever the SPACE key event
     * (press or release) arrives from the currently attached adapter.
     */
    private static final class SimpleMapper extends BaseInputMapper {

        private static final IInputIdentifier SPACE =
            new KeyInputIdentifier(Input.Keys.SPACE, true);

        private final IInputAction act;

        SimpleMapper(IInputAction act){
            this.act = act;

            /* supply mandatory collaborators expected by BaseInputMapper */
            /* dummy managers (combos/chains not used in this test) */
            this.comboMgr = new FastComboManager();
            this.chainMgr = new FastInputChainManager();
            this.buffer   = new SmartInputBuffer(4);

            /* install a history instance to avoid NPE in super.onInput() */
            try {
                var f = BaseInputMapper.class.getDeclaredField("history");
                f.setAccessible(true);
                f.set(this, new InputHistory());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot init history", e);
            }
        }

        /* direct identifier→action mapping (no combos/chains) */
        @Override public void onInput(IInputEvent evt){
            super.onInput(evt);                                 // keep statistics

            if (evt instanceof ButtonEvent be &&
                SPACE.equals(be.getControl()))
            {
                act.execute(null);                              // fire!
            }
        }

        /* mapping helpers unused in this trivial mapper ---------------- */
        @Override public void map  (String c,IInputIdentifier id,IInputAction a){}
        @Override public void unmap(String c,IInputIdentifier id)          {}
        @Override public void clear(String c)                              {}
    }

    /** stub adapter that can dispatch arbitrary events */
    private static final class StubAdapter extends BaseInputAdapter {
        StubAdapter(String name, int slot){ super(name); setSlot(slot); }
        @Override public void start(){ }
        @Override public void stop() { }
        void fire(IInputEvent e){ dispatch(e); }
    }

    /* ───────────────────────── BaseTest entry-point ─────────────────── */
    @Override
    protected void runTest() {
        /* LibGDX stubs for logger */
        setupApplication();

        /* Components under test */
        InputActionManager mgr = InputActionManager.get();
        mgr.clear();                                // clean slate

        CounterAction act = mgr.getOrCreate(CounterAction.class, CounterAction::new);

        SimpleMapper mapper = new SimpleMapper(act);

        StubAdapter pad0 = new StubAdapter("Pad-0", 0);
        StubAdapter pad1 = new StubAdapter("Pad-1", 1);

        /* attach to pad0 */
        mgr.attachMapper(mapper, pad0);
        mapper.setAdapter(pad0);
        Assertions.assertSame(pad0, mgr.adapterOf(mapper));

        pad0.fire(new ButtonEvent(0, new KeyInputIdentifier(Input.Keys.SPACE, true), true, 1f));
        Assertions.assertEquals(1, act.hits(), "Action should fire through pad0");

        pad1.fire(new ButtonEvent(1, new KeyInputIdentifier(Input.Keys.SPACE, true), true, 1f));
        Assertions.assertEquals(1, act.hits(), "Not yet bound to pad1");

        /* re-attach to pad1 (auto-detaches from pad0) */
        mgr.attachMapper(mapper, pad1);
        mapper.setAdapter(pad1);
        Assertions.assertSame(pad1, mgr.adapterOf(mapper));

        pad0.fire(new ButtonEvent(0, new KeyInputIdentifier(Input.Keys.SPACE, true), true, 1f));
        Assertions.assertEquals(1, act.hits(), "Old adapter must no longer trigger");

        pad1.fire(new ButtonEvent(1, new KeyInputIdentifier(Input.Keys.SPACE, true), true, 1f));
        Assertions.assertEquals(2, act.hits(), "Action fires through new adapter");

        /* detach mapper entirely */
        mgr.detachMapper(mapper);
        mapper.setAdapter(null);
        Assertions.assertNull(mgr.adapterOf(mapper));

        pad1.fire(new ButtonEvent(1, new KeyInputIdentifier(Input.Keys.SPACE, true), true, 1f));
        Assertions.assertEquals(2, act.hits(), "Detached mapper should ignore input");
    }
}
