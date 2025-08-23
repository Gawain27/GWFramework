package com.gwngames.core.build.monitor;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;

import java.util.Map;

public final class DashboardDefaults {
    private DashboardDefaults() {}

    /** Simple section header. Template expects: { "text": String } */
    public static IDashboardHeader header(String title) {
        return new IDashboardHeader() {
            @Override public String templateId() { return "header"; } // <- not "kv"
            @Override public Object model()      { return Map.of("text", title); }
        };
    }

    /** Big number stat. Template expects: { "value": Number } */
    public static IDashboardContent count(int n) {
        return new IDashboardContent() {
            @Override public String templateId() { return "count"; }  // <- not "number"
            @Override public Object model()      { return Map.of("value", n); }
        };
    }

    /** Key/Value snippet. Template expects: { "key": String, "value": any } */
    public static IDashboardContent kv(String key, Object value) {
        return new IDashboardContent() {
            @Override public String templateId() { return "kv"; }
            @Override public Object model()      { return Map.of("key", key, "value", value); }
        };
    }
}
