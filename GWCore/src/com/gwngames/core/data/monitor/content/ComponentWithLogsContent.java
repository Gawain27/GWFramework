package com.gwngames.core.data.monitor.content;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.view.IComponentLogView;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

import java.util.*;

/**
 * Wrapper content that composes the tile and the popup into a single render.
 * Requires a `component-with-logs` template that internally renders:
 *  - component-item (tile)
 *  - log-popup      (overlay)
 */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.DASHBOARD_LOGS_COMPONENT)
public class ComponentWithLogsContent extends BaseComponent implements IDashboardContent, IComponentLogView {
    // Precomputed fields (fallbacks used if not initialized)
    private String title  = "Component";
    private String safeId = "cmp-" + Integer.toHexString(System.identityHashCode(this));
    private String keys   = ""; // comma-separated comp keys to fetch from /dashboard/logs
    private int    errors = 0;

    @Override public String templateId() { return "component-with-logs"; }

    /** Backward-compat: if someone calls setComponent with a BaseComponent. */
    @Override public void setComponent(BaseComponent component) {
        if (component instanceof BaseComponent bc) {
            this.title  = bc.dashboardTitle();
            this.safeId = bc.safeId();
            String inst = bc.dashboardKey();
            String cls  = bc.getClass().getName();
            this.keys   = inst + "," + cls;
            this.errors = bc.errorCount();
        }
    }

    /** Preferred init path (works even if the caller is a proxy). */
    @Override public void init(String title, String safeId, String keysCsv, int errors) {
        if (title  != null && !title.isBlank()) this.title  = title;
        if (safeId != null && !safeId.isBlank()) this.safeId = safeId;
        if (keysCsv!= null)                     this.keys   = keysCsv;
        this.errors = Math.max(0, errors);
    }

    @Override public Object model() {
        Map<String,Object> tile  = new HashMap<>();
        Map<String,Object> popup = new HashMap<>();
        Map<String,Object> out   = new HashMap<>();

        tile.put("title",  title);
        tile.put("safeId", safeId);
        tile.put("errors", errors);

        popup.put("title",  "Logs • " + title);
        popup.put("safeId", safeId);
        popup.put("keys",   keys);

        out.put("tile",  tile);
        out.put("popup", popup);
        return out;
    }
}
