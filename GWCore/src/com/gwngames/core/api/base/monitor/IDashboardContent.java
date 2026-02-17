package com.gwngames.core.api.base.monitor;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.build.Init;

import java.util.List;

/**
 * <p>A more flexible <em>content block</em> used in the dashboard, injected
 * automatically via {@link com.gwngames.core.api.build.Inject}
 */
@Init(module = DefaultModule.INTERFACE, component = CoreComponent.DASHBOARD_CONTENT, allowMultiple = true, forceDefinition = true)
public non-sealed interface IDashboardContent<T extends IDashboardItem<T>> extends IDashboardNode {

    /**
     * render the header of the content
     * @return the html of the content header
     * */
    String renderHeader();

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
