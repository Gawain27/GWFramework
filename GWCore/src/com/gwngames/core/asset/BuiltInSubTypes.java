package com.gwngames.core.asset;

import com.gwngames.core.api.asset.AssetCategory;
import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ModuleNames;

import java.util.Collection;
import java.util.List;

@Init(module = ModuleNames.CORE)
public enum BuiltInSubTypes implements IAssetSubType {

    /* AUDIO ------------------------------------------------------ */
    MUSIC ("music",  AssetCategory.AUDIO,   com.badlogic.gdx.audio.Music.class,
        List.of(Ext.MP3, Ext.OGG, Ext.WAV)),

    SOUND ("sound",  AssetCategory.AUDIO,   com.badlogic.gdx.audio.Sound.class,
        List.of(Ext.WAV, Ext.OGG)),

    /* TEXTURE ---------------------------------------------------- */
    TEXTURE("texture", AssetCategory.TEXTURE,
        com.badlogic.gdx.graphics.Texture.class,
        List.of(Ext.PNG, Ext.JPG, Ext.JPEG)),

    ATLAS  ("atlas",   AssetCategory.TEXTURE,
        com.badlogic.gdx.graphics.g2d.TextureAtlas.class,
        List.of(Ext.ATLAS)),

    REGION ("region",  AssetCategory.TEXTURE,
        com.badlogic.gdx.graphics.g2d.TextureRegion.class,
        List.of(Ext.PNG, Ext.JPG, Ext.JPEG)),

    /* MISC catch-all -------------------------------------------- */
    MISC   ("misc",    AssetCategory.MISC, null, List.of());

    /* boilerplate */
    private final String id; private final AssetCategory cat;
    private final Class<?> cls; private final Collection<IFileExtension> exts;
    BuiltInSubTypes(String i, AssetCategory c, Class<?> k,
                    Collection<IFileExtension> e){
        id=i;cat=c;cls=k;exts=e;
    }
    public String id(){ return id; }
    public AssetCategory category(){ return cat; }
    public Class<?> libGdxClass(){ return cls; }
    public Collection<IFileExtension> extensions(){ return exts; }
}
