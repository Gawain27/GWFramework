package com.gwngames.core.build.monitor;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;

public final class DashboardDefaults {
    public static IDashboardHeader header(String title) {
        return new IDashboardHeader() {
            public String templateId() { return "kv"; }
            public Object model()      { return title; }
        };
    }
    public static IDashboardContent count(int n) {
        return new IDashboardContent() {
            public String templateId() { return "number"; }
            public Object model()      { return n; }
        };
    }
}
