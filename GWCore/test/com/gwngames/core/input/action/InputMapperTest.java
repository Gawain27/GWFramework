package com.gwngames.core.input.action;

import com.badlogic.gdx.Input;
import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.*;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.BaseInputAdapter;
import com.gwngames.core.input.controls.KeyInputIdentifier;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration test: BaseInputMapper + BaseInputAction + BaseInputAdapter.
 */
public class InputMapperTest extends BaseTest {

    /* ───────────────────────── Action under test ────────────────────── */
    private static final class CounterAction extends BaseInputAction {
        private final AtomicInteger counter = new AtomicInteger();
        @Override protected void perform(IInputEvent e) { counter.incrementAndGet(); }
        int count(){ return counter.get(); }
    }

    /* ───────────────────────── Stub adapter (exposes dispatch) ───────── */
    private static final class StubAdapter extends BaseInputAdapter {
        StubAdapter(){ super("Stub"); }
        @Override public void start() { }   // no-op
        @Override public void stop()  { }
        /* expose helper */
        void fire(IInputEvent e){ dispatch(e); }
    }

    /* ───────────────────────── Mapper implementation  ────────────────── */
    private static final class SimpleMapper extends BaseInputMapper {
        SimpleMapper(IInputIdentifier id, IInputAction act){
            map("play", id, act);
        }
    }

    /* ───────────────────────── test logic (BaseTest) ─────────────────── */
    @Override
    protected void runTest() {
        /* install LibGDX stubs */
        setupApplication();

        /* create adapter + mapper + action */
        StubAdapter adapter = new StubAdapter();
        adapter.setSlot(0);

        CounterAction action = new CounterAction();
        SimpleMapper  mapper = new SimpleMapper(
            new KeyInputIdentifier(Input.Keys.SPACE), action);

        mapper.setAdapter(adapter);          // auto-listens

        /* 1) default context = "default" -> NO mapping */
        adapter.fire(new ButtonEvent(0,
            new KeyInputIdentifier(Input.Keys.SPACE), true,1f));
        Assertions.assertEquals(0, action.count(), "No mapping in default ctx");

        /* 2) switch to mapped context "play" -> action fires */
        mapper.switchContext("play");
        adapter.fire(new ButtonEvent(0,
            new KeyInputIdentifier(Input.Keys.SPACE), true,1f));
        Assertions.assertEquals(1, action.count(), "Action should fire in play ctx");

        /* 3) switch to unmapped context "menu" -> no fire */
        mapper.switchContext("menu");
        adapter.fire(new ButtonEvent(0,
            new KeyInputIdentifier(Input.Keys.SPACE), true,1f));
        Assertions.assertEquals(1, action.count(), "No mapping in menu ctx");

        /* 4) back to play but action disabled for slot -> no fire */
        action.setEnabled(0,false);
        mapper.switchContext("play");
        adapter.fire(new ButtonEvent(0,
            new KeyInputIdentifier(Input.Keys.SPACE), true,1f));
        Assertions.assertEquals(1, action.count(), "Disabled action must not fire");

        /* 5) re-enable and ensure it fires again */
        action.setEnabled(0,true);
        adapter.fire(new ButtonEvent(0,
            new KeyInputIdentifier(Input.Keys.SPACE), true,1f));
        Assertions.assertEquals(2, action.count(), "Action fires after re-enable");
    }
}

