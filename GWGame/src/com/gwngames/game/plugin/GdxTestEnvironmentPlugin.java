package com.gwngames.game.plugin;

import com.badlogic.gdx.Gdx;
import com.gwngames.core.api.plugin.TestEnvironmentPlugin;
import com.gwngames.game.GameModule;
import com.gwngames.game.plugin.gdx.DummyApp;
import com.gwngames.game.plugin.gdx.DummyFiles;
import com.gwngames.game.plugin.gdx.DummyInput;

/**
 * Installs libGDX stubs for tests.
 * Lives in gwgame so gwcore doesn't depend on libGDX.
 */
public final class GdxTestEnvironmentPlugin implements TestEnvironmentPlugin {

    @Override public String id() { return "test-env"; }

    @Override public String module() { return GameModule.GAME; } // string constant

    @Override
    public void install() {
        Gdx.app   = new DummyApp();
        Gdx.files = new DummyFiles();
        if (Gdx.input == null) {
            Gdx.input = new DummyInput();
        }
    }
}
