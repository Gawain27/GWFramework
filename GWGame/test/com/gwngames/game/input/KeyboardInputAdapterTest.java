package com.gwngames.game.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Gdx;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.game.api.event.input.IButtonEvent;
import com.gwngames.game.api.event.input.IInputEvent;
import com.gwngames.game.api.input.IInputListener;
import com.gwngames.game.input.adapter.KeyboardInputAdapter;
import com.gwngames.game.input.controls.KeyInputIdentifier;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class KeyboardInputAdapterTest extends BaseTest {

    /** Minimal Input that supports installing an InputProcessor. */
    private static final class DummyInputWithProcessor implements Input {
        private InputProcessor proc;

        @Override public void setInputProcessor(InputProcessor processor) { this.proc = processor; }
        @Override public InputProcessor getInputProcessor() { return proc; }

        // --- Only what PeripheralDeviceDetector might touch; keep safe defaults ---
        @Override public boolean isPeripheralAvailable(Peripheral peripheral) { return false; }

        // --- Everything else: irrelevant for this unit test ---
        @Override public float getAccelerometerX() { return 0; }
        @Override public float getAccelerometerY() { return 0; }
        @Override public float getAccelerometerZ() { return 0; }
        @Override public float getGyroscopeX() { return 0; }
        @Override public float getGyroscopeY() { return 0; }
        @Override public float getGyroscopeZ() { return 0; }
        @Override public int getMaxPointers() { return 0; }
        @Override public int getX() { return 0; }
        @Override public int getX(int pointer) { return 0; }
        @Override public int getDeltaX() { return 0; }
        @Override public int getDeltaX(int pointer) { return 0; }
        @Override public int getY() { return 0; }
        @Override public int getY(int pointer) { return 0; }
        @Override public int getDeltaY() { return 0; }
        @Override public int getDeltaY(int pointer) { return 0; }
        @Override public boolean isTouched() { return false; }
        @Override public boolean justTouched() { return false; }
        @Override public boolean isTouched(int pointer) { return false; }
        @Override public float getPressure() { return 0; }
        @Override public float getPressure(int pointer) { return 0; }
        @Override public boolean isButtonPressed(int button) { return false; }
        @Override public boolean isButtonJustPressed(int button) { return false; }
        @Override public boolean isKeyPressed(int key) { return false; }
        @Override public boolean isKeyJustPressed(int key) { return false; }
        @Override public void getTextInput(TextInputListener listener, String title, String text, String hint) {}
        @Override public void getTextInput(TextInputListener listener, String title, String text, String hint, OnscreenKeyboardType type) {}
        @Override public void setOnscreenKeyboardVisible(boolean visible) {}
        @Override public void setOnscreenKeyboardVisible(boolean visible, OnscreenKeyboardType type) {}
        @Override public void openTextInputField(com.badlogic.gdx.input.NativeInputConfiguration configuration) {}
        @Override public void closeTextInputField(boolean send) {}
        @Override public void setKeyboardHeightObserver(KeyboardHeightObserver observer) {}
        @Override public void vibrate(int milliseconds) {}
        @Override public void vibrate(int milliseconds, boolean fallback) {}
        @Override public void vibrate(int milliseconds, int amplitude, boolean fallback) {}
        @Override public void vibrate(VibrationType vibrationType) {}
        @Override public float getAzimuth() { return 0; }
        @Override public float getPitch() { return 0; }
        @Override public float getRoll() { return 0; }
        @Override public void getRotationMatrix(float[] matrix) {}
        @Override public long getCurrentEventTime() { return 0; }
        @Override public void setCatchKey(int keycode, boolean catchKey) {}
        @Override public boolean isCatchKey(int keycode) { return false; }
        @Override public int getRotation() { return 0; }
        @Override public Orientation getNativeOrientation() { return Orientation.Landscape; }
        @Override public void setCursorCatched(boolean catched) {}
        @Override public boolean isCursorCatched() { return false; }
        @Override public void setCursorPosition(int x, int y) {}
    }

    @Override
    protected void runTest() throws Exception {
        setupApplication();

        // Make sure Gdx.input exists and supports setInputProcessor/getInputProcessor
        DummyInputWithProcessor input = new DummyInputWithProcessor();
        Gdx.input = input;

        // Framework-managed adapter so @Inject fields (InputManager) are wired
        KeyboardInputAdapter adapter = BaseComponent.getInstance(KeyboardInputAdapter.class, true);
        adapter.setSlot(0);

        AtomicInteger downCnt = new AtomicInteger();
        AtomicInteger upCnt   = new AtomicInteger();
        AtomicReference<IButtonEvent> lastButton = new AtomicReference<>();

        IInputListener listener = new IInputListener() {
            @Override public int getMultId() { return 0; }

            @Override
            public void onInput(IInputEvent evt) {
                if (evt instanceof IButtonEvent be) {
                    lastButton.set(be);
                    if (be.isPressed()) downCnt.incrementAndGet();
                    else upCnt.incrementAndGet();
                }
            }

            @Override public String identity() { return "test-listener"; }
        };

        adapter.addListener(listener);

        // start() must register as InputProcessor
        adapter.start();
        Assertions.assertSame(adapter, input.getInputProcessor(),
            "Adapter should install itself as InputProcessor");

        // fire a keyDown
        int key = Input.Keys.Q;
        input.getInputProcessor().keyDown(key);

        // drive the event system until the event completes (or timeout)
        IButtonEvent be = awaitCompletedButtonEvent(lastButton, false, 100);

        // assertions
        Assertions.assertEquals(1, downCnt.get(), "One DOWN event expected");
        Assertions.assertNotNull(be);
        Assertions.assertNotNull(be.getControl(), "Button control must be set");
        Assertions.assertInstanceOf(KeyInputIdentifier.class, be.getControl(),
            "Keyboard events must use KeyInputIdentifier");
        Assertions.assertEquals(key, ((KeyInputIdentifier) be.getControl()).getKeycode(),
            "Keycode must match");

        // keyUp (optional, but keeps your original behavior)
        input.getInputProcessor().keyUp(key);
        IButtonEvent beUp = awaitCompletedButtonEvent(lastButton, false,100);
        Assertions.assertEquals(1, upCnt.get(), "One UP event expected");

        // stop() must clear processor
        adapter.stop();
        Assertions.assertNull(input.getInputProcessor(),
            "InputProcessor should be cleared on stop()");
    }

    private IButtonEvent awaitCompletedButtonEvent(
        AtomicReference<IButtonEvent> ref,
        boolean expectedPressed,
        long timeoutMs
    ) throws Exception {

        long until = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < until) {
            master.process(0f);

            IButtonEvent ev = ref.get();
            if (ev != null && ev.isPressed() == expectedPressed) {
                // Your framework assumes events are AbstractEvent (InputSubQueue/EventStatusLogger do the same)
                var ae = (com.gwngames.core.event.base.AbstractEvent) ev;
                if (ae.getStatus() == com.gwngames.core.data.event.EventStatus.COMPLETED) {
                    return ev;
                }
            }

            Thread.sleep(5);
        }

        Assertions.fail("Timed out waiting for ButtonEvent COMPLETED (pressed=" + expectedPressed + ").");
        return null; // unreachable
    }
}
