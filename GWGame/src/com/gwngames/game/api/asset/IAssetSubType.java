package com.gwngames.game.api.asset;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.data.asset.AssetCategory;
import com.gwngames.game.GameComponent;

import java.util.List;

/**
 *  one instance per “loader strategy” – e.g. Music, Sound, TextureAtlas<p>
 *  Defines the specific asset type to load for the category.
 **/
@Init(component = GameComponent.ASSET_SUBTYPE, module = DefaultModule.INTERFACE, allowMultiple = true, isEnum = true)
public interface IAssetSubType extends IBaseComp {
    String               id();          // "music", "sound", "atlas", …
    AssetCategory category();    // AUDIO, …
    Class<?>             libGdxClass(); // Music.class, Texture.class, …
    /**
     * ONLY ONE EXTENSION! Maintaining a list is too costly...
     */
    List<IFileExtension> extension();
}
