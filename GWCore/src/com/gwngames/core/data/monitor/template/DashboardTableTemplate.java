package com.gwngames.core.data.monitor.template;

import com.gwngames.core.api.base.monitor.IDashboardContent;
import com.gwngames.core.api.base.monitor.IDashboardHeader;

import java.util.*;

/**
 * A lightweight container for <em>all categories that belong to one table</em>.
 *
 * @param <CAT>  enum that identifies a top-level category (e.g. CPU / RAM)
 * @param <ICAT> enum that identifies an item-category (e.g. User / System)
 */
public final class DashboardTableTemplate<CAT extends Enum<CAT>,
    ICAT extends Enum<ICAT>> {

    private final Map<CAT, DashboardCategoryTemplate<ICAT>> categories = new HashMap<>();

    /** Returns (or creates) the category wrapper for {@code key}. */
    public DashboardCategoryTemplate<ICAT> category(CAT key,
                                                    IDashboardHeader header,
                                                    IDashboardContent stats) {

        Objects.requireNonNull(header);
        Objects.requireNonNull(stats);
        return categories.computeIfAbsent(
            key, k -> new DashboardCategoryTemplate<>(header, stats));
    }

    /** Read-only view used by the renderer. */
    public Collection<DashboardCategoryTemplate<ICAT>> allCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }
}
