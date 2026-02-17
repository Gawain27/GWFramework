package com.gwngames.core.build.monitor.view;

import com.gwngames.core.CoreModule;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.monitor.IDashboardItem;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.build.monitor.BaseDashboardContent;

import com.gwngames.core.base.log.LogBus;
import com.gwngames.core.util.StringUtils;

/** Lists registered components with an error counter badge. */
@Init(module = CoreModule.CORE, subComp = CoreSubComponent.DASHBOARD_LOGS_COMPONENT)
public class ComponentContent extends BaseDashboardContent<BaseComponent> {

    @Override
    public String renderContentHeader() {
        return "<h2>Components</h2>";
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("<div class=\"muted\">No items.</div>");
        } else {
            sb.append("<div class=\"list five-cols\">"); // <-- 5 columns
            for (IDashboardItem<BaseComponent> it : items) {
                String payload = null;
                try {
                    payload = renderItem(it.getItem());
                } catch (Throwable t) {
                    log.error("Error getting item payload from {}", it.getClass().getSimpleName(), t);
                }
                sb.append("<div class=\"item\">").append(payload).append("</div>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    @Override
    protected String renderItem(BaseComponent comp) {
        final String key = comp.dashboardKey();
        final int errs = LogBus.errorCount(key);
        final String badgeCls = errs > 0 ? "badge err" : "badge ok";

        final String simple = StringUtils.escapeHtml(comp.getClass().getSimpleName());
        final String keyEsc = StringUtils.escapeHtml(key);

        return """
            <div class="row">
              <div class="comp">
                <div class="name">%s</div>
                <div class="subtle">%s</div>
              </div>
              <span class="%s" title="Errors recorded for %s">%d</span>
            </div>
            """.formatted(simple, keyEsc, badgeCls, keyEsc, errs);
    }
}

