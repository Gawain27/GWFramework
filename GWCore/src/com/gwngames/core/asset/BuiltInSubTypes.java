/* SPDX-License-Identifier: Apache-2.0 */
package com.gwngames.core.asset;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IFileExtension;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.AssetCategory;
import com.gwngames.core.data.ModuleNames;

import java.util.*;

/**
 * Built-in, engine-level asset sub-types (music, sound, texture, …).
 *
 * <p><strong>Changes vs. previous version</strong></p>
 * <ul>
 *   <li>{@code extension()} now returns an <em>immutable list</em> – a
 *       sub-type may support multiple file endings.</li>
 *   <li>Convenience constructor with a single {@link IFileExtension} keeps
 *       enum declarations short.</li>
 *   <li>A static block guarantees that every extension appears
 *       <em>exactly once</em> across all constants, failing fast on
 *       duplicates during class-initialisation.</li>
 * </ul>
 */
@Init(module = ModuleNames.CORE)
public enum BuiltInSubTypes implements IAssetSubType {

    /* AUDIO ------------------------------------------------------ */
    MUSIC ("music",  AssetCategory.AUDIO,   Music.class,   Ext.MP3),
    SOUND ("sound",  AssetCategory.AUDIO,   Sound.class,   Ext.WAV),

    /* TEXTURE ---------------------------------------------------- */
    TEXTURE("texture", AssetCategory.TEXTURE, TextureRegion.class,     Ext.PNG),
    ATLAS  ("atlas",   AssetCategory.TEXTURE, TextureAtlas.class,Ext.ATLAS),

    /* DATA ------------------------------------------------------- */
    JSON   ("json", AssetCategory.DATA, FileHandle.class, Ext.JSON),
    TXT    ("txt",  AssetCategory.DATA, FileHandle.class, Ext.TXT, Ext.CSS),

    /* MISC catch-all -------------------------------------------- */
    MISC   ("misc", AssetCategory.MISC, FileHandle.class /* no ext */ );

    /* ─────────────────────────── fields ─────────────────────────── */
    private final String id;
    private final AssetCategory category;
    private final Class<?> libGdxClass;
    private final List<IFileExtension> extensions;

    /* ───────────────────────── constructors ─────────────────────── */
    BuiltInSubTypes(String id,
                    AssetCategory cat,
                    Class<?> cls,
                    IFileExtension... exts) {          // var-arg ⇒ 1-liner for common case
        this.id          = id;
        this.category    = cat;
        this.libGdxClass = cls;
        this.extensions  = List.of(exts);              // empty list if none given
    }

    /* ───────────────────────── interface impl ───────────────────── */
    @Override public String            id()            { return id; }
    @Override public AssetCategory     category()      { return category; }
    @Override public Class<?>          libGdxClass()   { return libGdxClass; }

    /**
     * Immutable list – may be empty for {@code MISC}.
     */
    @Override public List<IFileExtension> extension()  { return extensions; }

    /* ───────────────────────── validation ───────────────────────── */
    static {
        Map<IFileExtension, BuiltInSubTypes> seen = new HashMap<>();
        for (BuiltInSubTypes st : values()) {
            for (IFileExtension ext : st.extensions) {
                BuiltInSubTypes dup = seen.put(ext, st);
                if (dup != null) {
                    throw new IllegalStateException(
                        "File extension "+ext+" declared for both "
                            + dup.name()+" and "+st.name());
                }
            }
        }
    }
}
