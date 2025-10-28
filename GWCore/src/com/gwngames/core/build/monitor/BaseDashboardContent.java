package com.gwngames.core.build.monitor;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardItem;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Base "box" implementation that simply lists all injected items.
 * Concrete subclasses can override render()/sort() if needed.
 */
public abstract class BaseDashboardContent<T extends IDashboardItem<T>> extends BaseComponent
    implements IDashboardContent<T> {

    protected final FileLogger log = FileLogger.get(LogFiles.MONITOR);

    @Inject(loadAll = true)
    protected List<T> items = new ArrayList<>();

    @PostInject
    void init(){
        sort();
    }

    @Override
    public String renderHeader(){
        String header = this.renderContentHeader();

        if (StringUtils.isEmpty(header))
            return "<h2>" + this + "</h2>";

        if (!StringUtils.isValidHtml(header))
            throw new RuntimeException("Malformed dashboard header: " + this.getClass().getSimpleName());
        return header;
    }

    /**
     * Render the actual html of the content header
     * */
    public abstract String renderContentHeader();

    @Override
    public String render(){
        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("<div class=\"muted\">No items.</div>");
        } else {
            sb.append("<div class=\"list\">");
            for (IDashboardItem<T> it : items) {
                String payload = null;
                try {
                    payload = renderItem(it.getItem());
                    if (!StringUtils.isValidHtml(payload))
                        throw new RuntimeException("Malformed dashboard item: " + it.dashboardKey());
                } catch (Throwable t) {
                    log.error("Error getting item payload from {}", it.getClass().getSimpleName(), t);
                }
                sb.append("<div class=\"item\">")
                    .append(payload)
                    .append("</div>");
            }
            sb.append("</div>");
        }
        if (!StringUtils.isValidHtml(sb.toString()))
            throw new RuntimeException("Invalid dashboard content: " + this.getClass().getSimpleName());
        return sb.toString();
    }

    /**
     * Render the actual html of the item
     * */
    protected abstract String renderItem(T item);

    @Override
    public List<T> sort() {
        // Default deterministic order
        items.sort(Comparator
            .comparing((T i) -> i.getClass().getSimpleName())
            .thenComparing(i -> {
                Object v = i.getItem();
                return (v != null) ? v.toString() : "";
            }));
        return items;
    }

    @Override
    public String toString(){
        return this.getClass().getSimpleName();
    }
}
