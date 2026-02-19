package com.gwngames.game.base.cfg;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.cfg.CoreConfiguration;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.game.GameModule;
import com.gwngames.game.data.input.InputParameters;

@Init(module = GameModule.GAME)
public class GameConfiguration extends CoreConfiguration {

    @Override
    public void registerParameters(){
        super.registerParameters();

        setDefault(InputParameters.COMBO_DEFAULT_TTL_FRAMES, 8);
        setDefault(InputParameters.INPUT_MAX_DEVICES, 4);
        setDefault(InputParameters.INPUT_DEVICE_POLLING, 15f);

        setDefault(EventParameters.LOGIC_EVENT_MAX_THREAD, 8);
        setDefault(EventParameters.INPUT_EVENT_MAX_THREAD, 4);
        setDefault(EventParameters.RENDER_EVENT_MAX_THREAD, 16);
    }
}
