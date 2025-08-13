package com.gwngames.core.asset;

import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE)
public enum Ext implements IFileExtension {
    MP3("mp3"), OGG("ogg"), WAV("wav"),
    PNG("png"), JPG("jpg"), JPEG("jpeg"),
    ATLAS("atlas"),
    JSON("json"), TXT("txt"), CSS("css");
    private final String e;
    Ext(String e){ this.e=e; }
    @Override public String ext(){ return e; }
}
