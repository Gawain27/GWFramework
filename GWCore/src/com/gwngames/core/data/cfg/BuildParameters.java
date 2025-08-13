// com/gwngames/core/data/cfg/BuildParameters.java
package com.gwngames.core.data.cfg;

import com.gwngames.core.api.cfg.IParam;
import com.gwngames.core.api.cfg.ParamKey;

public final class BuildParameters {
    private BuildParameters() {}

    public static final IParam<Boolean> PROD_ENV =
        ParamKey.of("prod.env", Boolean.class);
    public static final IParam<Integer> DASHBOARD_PORT =
        ParamKey.of("dashboard.port", Integer.class);
}
