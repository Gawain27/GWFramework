package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * <p>A short <em>header block</em> shown above a category or item-category.</p>
 *
 * <p>The pair {@linkplain #templateId() templateId} + {@linkplain #model()}
 * tells the renderer <em>how</em> to visualise the block.</p>
 */
@Init(module = ModuleNames.INTERFACE, allowMultiple = true, component = ComponentNames.DASHBOARD_HEADER, forceDefinition = true)
public non-sealed interface IDashboardHeader extends IDashboardNode {

    /** Logical key understood by the template registry. */
    String templateId();

    /** Payload consumed by the template ({@link java.util.Map}, record, DTO, …). */
    Object model();
}
