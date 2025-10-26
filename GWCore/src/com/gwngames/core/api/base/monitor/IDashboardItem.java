package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * A concrete dashboard item.
 * Auto injected in the container that wants it via the framework
 * @see com.gwngames.core.api.build.Inject
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_ITEM, allowMultiple = true)
public non-sealed interface IDashboardItem<T> extends IDashboardNode {
    /**
     * Get the concrete item, to be used in the {@link IDashboardContent}
     * */
    T getItem();
}
