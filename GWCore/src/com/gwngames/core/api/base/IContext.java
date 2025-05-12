package com.gwngames.core.api.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.HashMap;
import java.util.Map;

/**
 * Context regarding the whole game application:<p>
 *     - User information<br>
 *     - Data regarding the actual copy<br>
 *     - Context and history of logs/messages/requests<br>
 *     - Others
 *
 * @author samlam
 * */
@Init(component = ComponentNames.CONTEXT, module = ModuleNames.INTERFACE)
public interface IContext {
    // do not mess with these objects or you will break game startup
    String _DIRECTOR = "_DIRECTOR";
    String _LAUNCHER = "_LAUNCHER";
    // These below may be used
    String APPLICATION = "APPLICATION";

    Map<String, Object> CORE_CONTEXT = new HashMap<>();

    default void setContextObject(String contextName, Object contextObject){
        CORE_CONTEXT.put(contextName, contextObject);
    }

    @SuppressWarnings("unchecked")
    default <T> T getContextObject(String contextName, Class<T> objectClass){
        Object obj = CORE_CONTEXT.get(contextName);
        if (objectClass.isAssignableFrom(obj.getClass())){
            return (T) obj;
        }
        throw new IllegalStateException("Missing context object: " + contextName);
    }
}
