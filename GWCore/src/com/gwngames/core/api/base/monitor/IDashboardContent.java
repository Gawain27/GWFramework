package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

/**
 * <p>A more flexible <em>content block</em> used in the dashboard, injected
 * automatically via {@link com.gwngames.core.api.build.Inject}
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_CONTENT, allowMultiple = true, forceDefinition = true)
public non-sealed interface IDashboardContent<T extends IDashboardItem<T>> extends IDashboardNode {

    /**
     * render the content
     * @return the html of the content
     * */
    String render();

    /**
     * sort the items in the content
     * */
    List<T> sort();
}
