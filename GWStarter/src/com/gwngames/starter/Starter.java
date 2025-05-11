package com.gwngames.starter;

import com.gwngames.starter.launcher.LauncherDirector;

public class Starter {
    public static void main(String[] args) {
        LauncherDirector director = LauncherDirector.getInstance(LauncherDirector.class);
        director.start(args);
    }
}
