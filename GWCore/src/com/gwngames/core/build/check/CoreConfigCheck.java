package com.gwngames.core.build.check;

import com.gwngames.core.api.base.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.StartupCheck;
import com.gwngames.core.api.ex.BaseException;
import com.gwngames.core.api.ex.StartupException;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@StartupCheck
@Init(module = ModuleNames.CORE, component = ComponentNames.CHECK)
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
            throw new StartupException(this.getClass().getSimpleName());
        return true;
    }
}
