package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * <p>A more flexible <em>content block</em> placed under headers, inside
 * tables or next to items.  Shares the same rendering contract as
 * {@link IDashboardHeader}.</p>
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_CONTENT, allowMultiple = true, forceDefinition = true)
public non-sealed interface IDashboardContent extends IDashboardNode {
// TODO: make template enums...
    String templateId();
    Object model();
}
