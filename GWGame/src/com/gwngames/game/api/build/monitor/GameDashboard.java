package com.gwngames.game.api.build.monitor;

import com.badlogic.gdx.files.FileHandle;
import com.gwngames.assets.css.GwcoreCssAssets;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.build.monitor.CoreDashboard;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.asset.IAssetManager;

@Init(module = GameModule.GAME)
public class GameDashboard extends CoreDashboard {
    @Inject
    protected IAssetManager assetManager;

    @Override
    public String prepareCss(){
        FileHandle CSSFile = assetManager.get(GwcoreCssAssets.DASHBOARD_DARK_CSS);
        return CSSFile.readString();
    }
}
