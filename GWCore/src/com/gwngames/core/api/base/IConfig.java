package com.gwngames.core.api.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.HashMap;
import java.util.Map;

@Init(component = ComponentNames.CONFIGURATION, module = ModuleNames.INTERFACE)
public interface IConfig extends IBaseComp {
    Map<String, Object> paramMap = new HashMap<>();
    void registerParameters();

    default Object getParameter(String parameterName){
        Object paramValue = paramMap.get(parameterName);

        if(paramValue == null)
            throw new IllegalStateException("Parameter not found: " + parameterName);
        return paramValue;
    }

    @SuppressWarnings("unchecked")
    default <T> T getParameter(String parameterName, Class<T> paramClass){
        Object parameterValue = getParameter(parameterName);
        if(!paramClass.isAssignableFrom(parameterValue.getClass()))
            throw new IllegalStateException("Wrong parameter type: " + parameterName);

        return (T) parameterValue;
    }
}
