package com.gwngames.core.build.monitor;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;
import com.gwngames.core.base.BaseComponent;

import java.util.Map;

public final class DashboardDefaults {
    private DashboardDefaults() {}

    /** Simple section header. Template expects: { "text": String } */
    public static IDashboardHeader header(String title) {
        return new IDashboardHeader() {
            @Override public String templateId() { return "header"; }
            @Override public Object model()      { return Map.of("text", title); }
        };
    }

    /** Optional empty block (renders nothing). */
    public static IDashboardContent none() {
        return new IDashboardContent() {
            @Override public String templateId() { return "none"; }
            @Override public Object model()      { return null; }

            @Override
            public void setComponent(BaseComponent component) {
            }
        };
    }

    /** Big number stat. Template expects: { "value": Number } */
    public static IDashboardContent count(Number n) {
        return new IDashboardContent() {
            @Override public String templateId() { return "count"; }
            @Override public Object model()      { return Map.of("value", n); }

            @Override
            public void setComponent(BaseComponent component) {
            }
        };
    }

    /** Key/Value snippet. Template expects a map of label->value */
    public static IDashboardContent kv(Map<String, ?> pairs) {
        return new IDashboardContent() {
            @Override public String templateId() { return "kv"; }
            @Override public Object model()      { return pairs; }

            @Override
            public void setComponent(BaseComponent component) {
            }
        };
    }

    /** Single KV row convenience. */
    public static IDashboardContent kv(String key, Object value) {
        return kv(Map.of(key, value));
    }
}
