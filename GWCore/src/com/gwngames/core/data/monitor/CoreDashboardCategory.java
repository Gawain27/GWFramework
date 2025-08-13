package com.gwngames.core.data.monitor;

import com.gwngames.core.api.base.monitor.IDashboardCategory;
import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;
import com.gwngames.core.api.base.monitor.IDashboardItemCategory;

import java.util.List;

public enum CoreDashboardCategory implements IDashboardCategory {
    ;

    @Override
    public IDashboardHeader header() {
        return null;
    }

    @Override
    public IDashboardContent statistics() {
        return null;
    }

    @Override
    public List<IDashboardItemCategory> itemCategories() {
        return List.of();
    }
}
