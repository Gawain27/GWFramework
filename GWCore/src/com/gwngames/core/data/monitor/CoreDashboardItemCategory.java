package com.gwngames.core.data.monitor;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;
import com.gwngames.core.api.base.monitor.IDashboardItem;
import com.gwngames.core.api.base.monitor.IDashboardItemCategory;

import java.util.List;

public enum CoreDashboardItemCategory implements IDashboardItemCategory {
    ;

    @Override
    public void setMultId(int newId) {

    }

    @Override
    public IDashboardHeader header() {
        return null;
    }

    @Override
    public IDashboardContent statistics() {
        return null;
    }

    @Override
    public List<IDashboardItem> items() {
        return List.of();
    }
}
