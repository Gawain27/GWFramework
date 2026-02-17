package com.gwngames.core.api.base;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.build.Init;

import java.util.Locale;

@Init(module = DefaultModule.INTERFACE, component = CoreComponent.TRANSLATOR)
public interface ITranslationService extends IBaseComp{
    String tr(String key, Locale locale);

    void reload();
}
