package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.base.IBaseComp;

public sealed interface IDashboardNode extends IBaseComp
    permits IDashboard, IDashboardItem,
    IDashboardCategory, IDashboardItemCategory, IDashboardTable,
    IDashboardContent, IDashboardHeader {}

