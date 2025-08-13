package com.gwngames.core.data.monitor.template;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;

import java.util.*;

/**
 * A single dashboard category (e.g.&nbsp;“CPU”).
 * Holds its own header/statistics blocks and all nested item-categories.
 */
public final class DashboardCategoryTemplate<ICAT extends Enum<ICAT>> {

    public final IDashboardHeader header;
    public final IDashboardContent stats;

    private final Map<ICAT, DashboardItemCategoryTemplate> itemCats = new HashMap<>();

    DashboardCategoryTemplate(IDashboardHeader header, IDashboardContent stats) {
        this.header = Objects.requireNonNull(header);
        this.stats  = Objects.requireNonNull(stats);
    }

    /** Returns (or creates) the item-category wrapper for {@code key}. */
    public DashboardItemCategoryTemplate itemCategory(Enum<?> key,
                                             IDashboardHeader header,
                                             IDashboardContent stats) {

        Objects.requireNonNull(header);
        Objects.requireNonNull(stats);
        @SuppressWarnings("unchecked")
        ICAT cast = (ICAT) key;

        return itemCats.computeIfAbsent(
            cast, k -> new DashboardItemCategoryTemplate(header, stats));
    }

    public Collection<DashboardItemCategoryTemplate> allItemCategories() {
        return Collections.unmodifiableCollection(itemCats.values());
    }
}
