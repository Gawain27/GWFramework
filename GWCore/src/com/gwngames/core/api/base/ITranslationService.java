package com.gwngames.core.api.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.Locale;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.TRANSLATOR)
public interface ITranslationService extends IBaseComp{
    String tr(String key, Locale locale);
}
