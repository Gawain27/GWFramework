package com.gwngames.game.plugin.gdx;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;

import java.io.File;

public final class DummyFiles implements Files {

    private FileHandle fh(String p, FileType t) { return new FileHandle(new File(p)); }

    @Override public FileHandle classpath(String path) { return fh(path, FileType.Classpath); }
    @Override public FileHandle internal(String path)  { return fh(path, FileType.Internal); }
    @Override public FileHandle external(String path)  { return fh(path, FileType.External); }
    @Override public FileHandle absolute(String path)  { return fh(path, FileType.Absolute); }
    @Override public FileHandle local(String path)     { return fh(path, FileType.Local); }

    @Override public FileHandle getFileHandle(String path, FileType type) { return fh(path, type); }

    @Override public boolean isExternalStorageAvailable() { return true; }
    @Override public String getExternalStoragePath() { return ""; }

    @Override public boolean isLocalStorageAvailable() { return true; }
    @Override public String getLocalStoragePath() { return ""; }
}
