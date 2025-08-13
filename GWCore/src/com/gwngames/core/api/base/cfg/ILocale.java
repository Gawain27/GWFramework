package com.gwngames.core.api.base.cfg;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.Locale;

@Init(component = ComponentNames.LOCALE, module = ModuleNames.INTERFACE)
public interface ILocale extends IBaseComp {
    /**
     * Get the defined locale for the game<p>
     * Actual configuration is up to the use case...
     * */
    Locale getLocale();
}
