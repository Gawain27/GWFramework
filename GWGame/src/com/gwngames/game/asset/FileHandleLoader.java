package com.gwngames.game.asset;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

/** Minimal loader that lets AssetManager manage raw FileHandle assets. */
public final class FileHandleLoader extends SynchronousAssetLoader<FileHandle, FileHandleLoader.Parameters> {
    public static final class Parameters extends com.badlogic.gdx.assets.AssetLoaderParameters<FileHandle> {}

    public FileHandleLoader(FileHandleResolver resolver) { super(resolver); }

    @Override
    public FileHandle load(AssetManager am, String fileName, FileHandle fileHandle, Parameters params) {
        if (!fileHandle.exists()) {
            throw new GdxRuntimeException("File not found: " + fileHandle.path());
        }
        return fileHandle; // no transformation: pass the handle through
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle fileHandle, Parameters params) {
        return null;
    }
}
