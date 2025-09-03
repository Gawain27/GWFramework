package com.gwngames.core.data.input;

import com.gwngames.core.api.cfg.IParam;
import com.gwngames.core.api.cfg.ParamKey;

public final class InputParameters {
    private InputParameters() {}

    public static final IParam<Integer> COMBO_DEFAULT_TTL_FRAMES =
        ParamKey.of("combo.default.ttl", Integer.class);
}
