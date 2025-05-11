package com.gwngames.starter.build;

import com.badlogic.gdx.Application;

public interface ILauncher {
    // TODO: method to get correct game launcher
    Application createApplication();
    String getVersion();
}
