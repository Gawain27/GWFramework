package com.gwngames.starter;

import com.gwngames.starter.build.ILauncherMaster;
import com.gwngames.starter.launcher.LauncherMaster;

public class Starter {
    public static void main(String[] args) {
        ILauncherMaster master = LauncherMaster.getInstance(ILauncherMaster.class);
        //TODO: parameterize args before passing to the launcher master (custom handler)
        master.start(args);
    }
}
