package com.gwngames.core.api.base.monitor;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.build.Init;

/**
 * A concrete dashboard item.
 * Auto injected in the container that wants it via the framework
 * @see com.gwngames.core.api.build.Inject
 */
@Init(module = DefaultModule.INTERFACE, component = CoreComponent.DASHBOARD_ITEM, allowMultiple = true)
public non-sealed interface IDashboardItem<T> extends IDashboardNode {
    /**
     * Get the concrete item, to be used in the {@link IDashboardContent}
     * */
    T getItem();

    String dashboardKey();
}
