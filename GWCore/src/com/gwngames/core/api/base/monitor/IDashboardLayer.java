package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

/**
 * <p>A free-standing <em>layer</em>—a block of content that is neither part
 * of a table nor of a category (e.g.&nbsp;“General information”).</p>
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_LAYER, allowMultiple = true, forceDefinition = true)
public non-sealed interface IDashboardLayer extends IDashboardNode {

    /** Name shown as an {@code <h2>} element. */
    String getName();

    /** One or more content blocks rendered in this layer. */
    List<IDashboardContent> getContents();
}
