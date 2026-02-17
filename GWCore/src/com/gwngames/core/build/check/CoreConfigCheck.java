package com.gwngames.core.build.check;

import com.gwngames.core.CoreModule;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.ex.BaseException;
import com.gwngames.core.api.ex.StartupException;

@Init(module = CoreModule.CORE, subComp = CoreSubComponent.CONFIG_CHECK)
public class CoreConfigCheck extends StartupCheckImpl{
    @Inject
    IConfig config;

    @Override
    public boolean isCheckRequired() {
        return true;
    }

    @Override
    public boolean canCheckRaiseException() {
        return true;
    }

    @Override
    protected boolean executeCheck() throws BaseException {
        if (config == null)
            throw new StartupException(this.getClass().getSimpleName(), CheckTranslations.CONFIG_DATA_NOT_FOUND);
        return true;
    }
}
