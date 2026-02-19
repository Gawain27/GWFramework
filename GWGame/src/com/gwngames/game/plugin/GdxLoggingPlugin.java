package com.gwngames.game.plugin;

import com.gwngames.core.api.base.cfg.IApplicationLogger;
import com.gwngames.core.api.plugin.LoggingPlugin;
import com.gwngames.game.GameModule;
import com.gwngames.game.base.cfg.FileApplicationLogger;

public class GdxLoggingPlugin implements LoggingPlugin {
    @Override
    public IApplicationLogger createApplicationLogger() {
        return new FileApplicationLogger();
    }

    @Override
    public String id() {
        return "gdx-logging";
    }

    @Override
    public String module() {
        return GameModule.GAME;
    }
}
