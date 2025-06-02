package com.gwngames.starter.build;

public enum LauncherVersion {
    DESKTOP("0.0.3");

    final String version;
    LauncherVersion(String version){
        this.version = version;
    }

    public String getVersion() {
        return this.version;
    }
}
