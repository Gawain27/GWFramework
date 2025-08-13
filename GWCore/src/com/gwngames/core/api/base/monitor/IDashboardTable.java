package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

/**
 * <p>A logical <em>table</em> that groups several categories.  The table is
 * normally identified by an {@link Enum}, supplied by the items themselves
 * via {@link IDashboardItem#tableKey()}.</p>
 *
 * @param <CAT>  enum type for categories
 * @param <ICAT> enum type for item-categories
 */
@Init(module = ModuleNames.INTERFACE, allowMultiple = true, component = ComponentNames.DASHBOARD_TABLE, isEnum = true)
public non-sealed interface IDashboardTable<CAT extends Enum<CAT>, ICAT extends Enum<ICAT>>
    extends IDashboardNode {

    List<IDashboardCategory> categories();
}
