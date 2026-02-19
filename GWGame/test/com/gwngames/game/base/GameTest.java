package com.gwngames.game.base;

import com.badlogic.gdx.assets.AssetManager;
import com.gwngames.core.base.BaseTest;

import java.util.concurrent.ConcurrentHashMap;

public abstract class GameTest extends BaseTest {
    public static class StubAssetManager extends AssetManager {
        private final ConcurrentHashMap<String,Object> store = new ConcurrentHashMap<>();
        @Override public void load(String n, Class t){ store.putIfAbsent(n,new Object()); }
        @Override public boolean isLoaded(String n){ return store.containsKey(n); }
        @SuppressWarnings("unchecked") @Override
        public <T> T get(String n, Class<T> t){ return (T) store.get(n); }
        @SuppressWarnings("unchecked")
        @Override
        public <T> T finishLoadingAsset(String fileName) { return (T) store.get(fileName); }
        @Override public void unload(String n){ store.remove(n); }
        @Override public boolean update(int ms){ return true; }
        @Override public int getReferenceCount(String n){ return store.containsKey(n)?1:0; }
    }
}
