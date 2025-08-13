package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

/**
 * One visual <em>category</em> inside a table (e.g.&nbsp;“CPU”).
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_CATEGORY, isEnum = true, allowMultiple = true)
public non-sealed interface IDashboardCategory extends IDashboardNode {

    IDashboardHeader header();
    IDashboardContent statistics();
    List<IDashboardItemCategory> itemCategories();
}
