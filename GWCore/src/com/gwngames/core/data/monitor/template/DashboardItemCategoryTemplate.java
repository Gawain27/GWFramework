package com.gwngames.core.data.monitor.template;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;
import com.gwngames.core.api.base.monitor.IDashboardItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The lowest-level bucket in the hierarchy (e.g.&nbsp;“User space” under CPU).
 * Owns its header/statistics and the concrete {@link IDashboardItem}s.
 */
public final class DashboardItemCategoryTemplate {

    public final IDashboardHeader header;
    public final IDashboardContent stats;

    private final List<IDashboardItem> items = new ArrayList<>();

    DashboardItemCategoryTemplate(IDashboardHeader header, IDashboardContent stats) {
        this.header = Objects.requireNonNull(header);
        this.stats  = Objects.requireNonNull(stats);
    }

    /** Adds one metric item to this bucket. */
    public void addItem(IDashboardItem item) {
        items.add(Objects.requireNonNull(item));
    }

    /** Immutable view for the renderer. */
    public List<IDashboardItem> items() {
        return Collections.unmodifiableList(items);
    }
}
