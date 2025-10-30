package com.gwngames.core.api.asset;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

/**
 * Asset lookup facade for the engine.
 *
 * <p>Features:
 * <ul>
 *   <li>Lazy scheduling & loading on first request</li>
 *   <li>Locale-aware path selection when using {@link IAssetPath}</li>
 *   <li>Time-based eviction driven by {@link #update(float)}</li>
 *   <li>Path tokens are supported and expanded by the implementation:
 *       <code>${EXEC}</code>, <code>${WORK}</code>, <code>${HOME}</code>,
 *       <code>${CONFIG}</code>, <code>${TMP}</code></li>
 * </ul>
 * </p>
 *
 * <p><b>Usage</b>
 * <pre>
 *   Texture t1 = assets.get("assets/ui/logo.png", Texture.class);
 *   Sound   s1 = assets.get(MyAssets.CLICK);                  // class inferred
 *   Music   m1 = assets.get(MyAssets.THEME, Music.class);     // explicit type
 *
 *   // Drive async loading + eviction once per frame
 *   assets.update(Gdx.graphics.getDeltaTime());
 * </pre>
 * </p>
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.ASSET_MANAGER)
public interface IAssetManager extends IBaseComp {

    /**
     * Get an asset by (possibly tokenized) path, with explicit LibGDX class.
     * The implementation may expand tokens like ${EXEC}, ${WORK}, ${HOME}, ${CONFIG}, ${TMP}.
     */
    <T> T get(String path, Class<T> as);

    /**
     * Get an asset by logical {@link IAssetPath}. The implementation chooses a
     * locale-specific variant when available, then expands tokens (if any).
     */
    <T> T get(IAssetPath asset);

    /**
     * Same as {@link #get(IAssetPath)} but with an explicit LibGDX target type.
     */
    <T> T get(IAssetPath asset, Class<T> as);

    /**
     * Resolve the subtype for a given (logical or expanded) path.
     */
    IAssetSubType subtypeOf(String path);

    /**
     * Drive asynchronous loading and evict stale assets.
     *
     * @param delta seconds since last call
     * @return {@code true} if there is no pending async work
     */
    boolean update(float delta);

    List<String> listAssets(IAssetSubType wanted);

    List<String> listAssetsByCategory(com.gwngames.core.data.AssetCategory category);

    String toAbsolute(String logicalOrAbsolute);
}
