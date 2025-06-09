package com.gwngames.core.build;

import com.gwngames.core.api.base.ILocale;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

import java.util.Locale;

@Init(module = ModuleNames.CORE)
public class CoreLocale extends BaseComponent implements ILocale {

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }
}
