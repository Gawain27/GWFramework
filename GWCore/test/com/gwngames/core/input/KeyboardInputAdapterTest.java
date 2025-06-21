package com.gwngames.core.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.*;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.input.NativeInputConfiguration;
import com.gwngames.core.api.input.*;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.input.adapter.KeyboardInputAdapter;
import com.gwngames.core.input.controls.KeyInputIdentifier;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that KeyboardInputAdapter registers itself as InputProcessor,
 * fires correct ButtonEvents, and cleans up on stop().
 */
public class KeyboardInputAdapterTest extends BaseTest {

    /* ────────────────────── DummyInput with processor support ────────── */
    private static final class DummyInputWithProcessor implements Input {
        private InputProcessor proc;

        @Override
        public float getAccelerometerX() {
            return 0;
        }

        @Override
        public float getAccelerometerY() {
            return 0;
        }

        @Override
        public float getAccelerometerZ() {
            return 0;
        }

        @Override
        public float getGyroscopeX() {
            return 0;
        }

        @Override
        public float getGyroscopeY() {
            return 0;
        }

        @Override
        public float getGyroscopeZ() {
            return 0;
        }

        @Override
        public int getMaxPointers() {
            return 0;
        }

        @Override
        public int getX() {
            return 0;
        }

        @Override
        public int getX(int i) {
            return 0;
        }

        @Override
        public int getDeltaX() {
            return 0;
        }

        @Override
        public int getDeltaX(int i) {
            return 0;
        }

        @Override
        public int getY() {
            return 0;
        }

        @Override
        public int getY(int i) {
            return 0;
        }

        @Override
        public int getDeltaY() {
            return 0;
        }

        @Override
        public int getDeltaY(int i) {
            return 0;
        }

        @Override
        public boolean isTouched() {
            return false;
        }

        @Override
        public boolean justTouched() {
            return false;
        }

        @Override
        public boolean isTouched(int i) {
            return false;
        }

        @Override
        public float getPressure() {
            return 0;
        }

        @Override
        public float getPressure(int i) {
            return 0;
        }

        @Override
        public boolean isButtonPressed(int i) {
            return false;
        }

        @Override
        public boolean isButtonJustPressed(int i) {
            return false;
        }

        @Override
        public boolean isKeyPressed(int i) {
            return false;
        }

        @Override
        public boolean isKeyJustPressed(int i) {
            return false;
        }

        @Override
        public void getTextInput(TextInputListener textInputListener, String s, String s1, String s2) {

        }

        @Override
        public void getTextInput(TextInputListener textInputListener, String s, String s1, String s2, OnscreenKeyboardType onscreenKeyboardType) {

        }

        @Override
        public void setOnscreenKeyboardVisible(boolean b) {

        }

        @Override
        public void setOnscreenKeyboardVisible(boolean b, OnscreenKeyboardType onscreenKeyboardType) {

        }

        @Override
        public void openTextInputField(NativeInputConfiguration nativeInputConfiguration) {

        }

        @Override
        public void closeTextInputField(boolean b) {

        }

        @Override
        public void setKeyboardHeightObserver(KeyboardHeightObserver keyboardHeightObserver) {

        }

        @Override
        public void vibrate(int i) {

        }

        @Override
        public void vibrate(int i, boolean b) {

        }

        @Override
        public void vibrate(int i, int i1, boolean b) {

        }

        @Override
        public void vibrate(VibrationType vibrationType) {

        }

        @Override
        public float getAzimuth() {
            return 0;
        }

        @Override
        public float getPitch() {
            return 0;
        }

        @Override
        public float getRoll() {
            return 0;
        }

        @Override
        public void getRotationMatrix(float[] floats) {

        }

        @Override
        public long getCurrentEventTime() {
            return 0;
        }

        @Override
        public void setCatchKey(int i, boolean b) {

        }

        @Override
        public boolean isCatchKey(int i) {
            return false;
        }

        @Override public void setInputProcessor(InputProcessor processor) { this.proc = processor; }
        @Override public InputProcessor getInputProcessor() { return proc; }

        @Override
        public boolean isPeripheralAvailable(Peripheral peripheral) {
            return false;
        }

        @Override
        public int getRotation() {
            return 0;
        }

        @Override
        public Orientation getNativeOrientation() {
            return null;
        }

        @Override
        public void setCursorCatched(boolean b) {

        }

        @Override
        public boolean isCursorCatched() {
            return false;
        }

        @Override
        public void setCursorPosition(int i, int i1) {

        }
    }

    /* ───────────────────────── BaseTest entry-point ───────────────────── */
    @Override
    protected void runTest() {
        /* 1 — install dummy Gdx singletons */
        setupApplication();
        DummyInputWithProcessor input = new DummyInputWithProcessor();
        com.badlogic.gdx.Gdx.input = input;

        /* 2 — instantiate adapter & listener */
        KeyboardInputAdapter adapter = new KeyboardInputAdapter();

        AtomicInteger downCnt = new AtomicInteger();
        AtomicInteger upCnt   = new AtomicInteger();
        AtomicReference<ButtonEvent> lastEvt = new AtomicReference<>();

        IInputListener listener = evt -> {
            if (evt instanceof ButtonEvent be) {
                lastEvt.set(be);
                if (be.isPressed()) downCnt.incrementAndGet();
                else                upCnt.incrementAndGet();
            }
        };
        adapter.addListener(listener);

        /* 3 — start() must register as InputProcessor */
        adapter.start();
        Assertions.assertSame(adapter, input.getInputProcessor(), "Adapter should install itself as InputProcessor");

        /* 4 — simulate keyDown + keyUp on the processor */
        int key = Keys.Q;

        input.getInputProcessor().keyDown(key);
        Assertions.assertEquals(1, downCnt.get(), "One DOWN event expected");
        ButtonEvent evt = lastEvt.get();
        Assertions.assertTrue(evt.getControl() instanceof KeyInputIdentifier);
        Assertions.assertEquals(key, ((KeyInputIdentifier) evt.getControl()).getKeycode());

        input.getInputProcessor().keyUp(key);
        Assertions.assertEquals(1, upCnt.get(), "One UP event expected");

        /* 5 — stop() must clear processor */
        adapter.stop();
        Assertions.assertNull(input.getInputProcessor(), "InputProcessor should be cleared on stop()");
    }
}

