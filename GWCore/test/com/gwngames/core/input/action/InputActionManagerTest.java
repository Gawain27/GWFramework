package com.gwngames.core.input.action;

import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.base.BaseTest;
import org.junit.jupiter.api.Assertions;

/**
 * Unit-tests for {@link InputActionManager}.
 */
public class InputActionManagerTest extends BaseTest {

    /* ------------------------------------------------------------- *
     *  Simple concrete action for testing                            *
     * ------------------------------------------------------------- */
    private static final class DummyAction extends BaseInputAction implements IInputAction {
        DummyAction() { super(); }
        @Override protected void perform(IInputEvent event) { /* no-op */ }
    }

    /* ------------------------------------------------------------- *
     *  BaseTest entry-point                                          *
     * ------------------------------------------------------------- */
    @Override
    protected void runTest() {
        setupApplication();                           // LibGDX stubs

        InputActionManager mgr = InputActionManager.get();
        mgr.clear();                                  // clean slate

        /* ------ getOrCreate produces a single instance ------------ */
        DummyAction a1 = mgr.getOrCreate(DummyAction.class, DummyAction::new);
        DummyAction a2 = mgr.getOrCreate(DummyAction.class, DummyAction::new);

        Assertions.assertSame(a1, a2,
            "getOrCreate() must return the same instance for the same class");

        /* ------ getIfPresent returns that instance ---------------- */
        DummyAction fetched = mgr.getIfPresent(DummyAction.class);
        Assertions.assertSame(a1, fetched, "getIfPresent should return cached instance");

        /* ------ explicit register() cannot override --------------- */
        DummyAction another = new DummyAction();
        Assertions.assertThrows(IllegalStateException.class,
            () -> mgr.register(another),
            "Registering a second instance of the same class must fail");

        /* ------ clear() wipes the cache --------------------------- */
        mgr.clear();
        Assertions.assertNull(mgr.getIfPresent(DummyAction.class),
            "After clear(), cache must be empty");

        DummyAction a3 = mgr.getOrCreate(DummyAction.class, DummyAction::new);
        Assertions.assertNotSame(a1, a3,
            "After clear(), a new instance should be created");
    }
}
