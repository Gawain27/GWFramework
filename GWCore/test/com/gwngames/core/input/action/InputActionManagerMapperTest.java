package com.gwngames.core.input.action;

import com.badlogic.gdx.Input;
import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.KeyInputIdentifier;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration test: InputActionManager.attachMapper / detachMapper.
 */
public class InputActionManagerMapperTest extends BaseTest {

    /* ───────────────────────── Stub classes ─────────────────────────── */

    /** counts every execution */
    private static final class CounterAction extends BaseInputAction {
        private final AtomicInteger cnt = new AtomicInteger();
        @Override protected void perform(IInputEvent event){ cnt.incrementAndGet(); }
        int hits(){ return cnt.get(); }
    }

    /** minimal mapper with one mapping for SPACE key */
    private static final class SimpleMapper extends BaseInputMapper {
        SimpleMapper(IInputAction act){
            map("default", new KeyInputIdentifier(Input.Keys.SPACE), act);
        }
    }

    /** stub adapter that exposes dispatch() */
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
        Assertions.assertSame(pad0, mgr.adapterOf(mapper));

        pad0.fire(new ButtonEvent(0, new KeyInputIdentifier(Input.Keys.SPACE), true, 1f));
        Assertions.assertEquals(1, act.hits(), "Action should fire through pad0");

        pad1.fire(new ButtonEvent(1, new KeyInputIdentifier(Input.Keys.SPACE), true, 1f));
        Assertions.assertEquals(1, act.hits(), "Not yet bound to pad1");

        /* re-attach to pad1 (auto-detaches from pad0) */
        mgr.attachMapper(mapper, pad1);
        Assertions.assertSame(pad1, mgr.adapterOf(mapper));

        pad0.fire(new ButtonEvent(0, new KeyInputIdentifier(Input.Keys.SPACE), true, 1f));
        Assertions.assertEquals(1, act.hits(), "Old adapter must no longer trigger");

        pad1.fire(new ButtonEvent(1, new KeyInputIdentifier(Input.Keys.SPACE), true, 1f));
        Assertions.assertEquals(2, act.hits(), "Action fires through new adapter");

        /* detach mapper entirely */
        mgr.detachMapper(mapper);
        Assertions.assertNull(mgr.adapterOf(mapper));

        pad1.fire(new ButtonEvent(1, new KeyInputIdentifier(Input.Keys.SPACE), true, 1f));
        Assertions.assertEquals(2, act.hits(), "Detached mapper should ignore input");
    }
}
