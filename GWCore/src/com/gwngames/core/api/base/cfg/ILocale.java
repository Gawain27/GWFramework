package com.gwngames.core.api.base.cfg;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

import java.util.Locale;

@Init(component = CoreComponent.LOCALE, module = DefaultModule.INTERFACE)
public interface ILocale extends IBaseComp {
    /**
     * Get the defined locale for the game<p>
     * Actual configuration is up to the use case...
     * */
    Locale getLocale();
}
