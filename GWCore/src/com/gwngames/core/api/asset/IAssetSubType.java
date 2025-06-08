package com.gwngames.core.api.asset;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.Collection;

/**
 *  one instance per “loader strategy” – e.g. Music, Sound, TextureAtlas<p>
 *  Defines the specific asset type to load for the category.
 **/
@Init(component = ComponentNames.ASSET_SUBTYPE, module = ModuleNames.INTERFACE, allowMultiple = true, isEnum = true)
public interface IAssetSubType extends IBaseComp {
    String               id();          // "music", "sound", "atlas", …
    AssetCategory        category();    // AUDIO, …
    Class<?>             libGdxClass(); // Music.class, Texture.class, …
    Collection<IFileExtension> extensions();
}
