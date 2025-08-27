package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * A concrete dashboard item (leaf). Placement + non-null synthesis blocks + content.
 *
 * NOTE: No defaults here; implementations must return non-null objects.
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_ITEM, allowMultiple = true)
public non-sealed interface IDashboardItem extends IDashboardNode {

    /* ───────────── placement ───────────── */

    Enum<?> tableKey();
    Enum<?> categoryKey();
    Enum<?> itemCategoryKey();

    /* ───────────── synthesis (non-null) ───────────── */

    IDashboardHeader  categoryHeader();
    IDashboardContent categoryStatistics();
    IDashboardHeader  itemCategoryHeader();
    IDashboardContent itemCategoryStats();

    /* ───────────── visualisation (non-null) ───────────── */

    /** The actual content to render. Its own template decides how it looks. */
    IDashboardContent itemContent();
}
