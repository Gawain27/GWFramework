package com.gwngames.core.build;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.base.cfg.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;

import java.util.Locale;

@Init(module = CoreModule.CORE)
public class CoreLocale extends BaseComponent implements ILocale {

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }
}
