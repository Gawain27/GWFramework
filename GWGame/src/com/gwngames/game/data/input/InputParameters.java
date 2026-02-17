package com.gwngames.game.data.input;

import com.gwngames.core.api.cfg.IParam;
import com.gwngames.core.api.cfg.ParamKey;

public final class InputParameters {
    private InputParameters() {}

    public static final IParam<Integer> COMBO_DEFAULT_TTL_FRAMES =
        ParamKey.of("combo.default_ttl", Integer.class);
    public static final IParam<Integer> INPUT_MAX_DEVICES =
        ParamKey.of("input.max_devices", Integer.class);
    public static final IParam<Float> INPUT_DEVICE_POLLING =
        ParamKey.of("input.device_polling", Float.class);
}
