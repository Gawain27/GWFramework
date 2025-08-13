package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

/**
 * A sub-bucket inside a category (e.g.&nbsp;“User space”).
 */
@Init(module = ModuleNames.INTERFACE, isEnum = true, allowMultiple = true, component = ComponentNames.DASHBOARD_ITEM_CATEGORY)
public non-sealed interface IDashboardItemCategory extends IDashboardNode {

    IDashboardHeader header();
    IDashboardContent statistics();
    List<IDashboardItem> items();
}
