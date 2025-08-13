package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/**
 * <h2>{@code IDashboardItem}</h2>
 *
 * <p>The <strong>leaf node</strong> of the dashboard hierarchy &nbsp;&mdash;&nbsp;
 * a single, concrete metric or indicator.</p>
 *
 * <h3>1&nbsp;&mdash;&nbsp;Placement</h3>
 * <ul>
 *   <li>{@link #tableKey()}&nbsp;&nbsp;&nbsp;&rarr;&nbsp;which logical&nbsp;table</li>
 *   <li>{@link #categoryKey()}&nbsp;&nbsp;&rarr;&nbsp;which category inside that table</li>
 *   <li>{@link #itemCategoryKey()} &rarr;&nbsp;which item-category bucket</li>
 * </ul>
 *
 * <h3>2&nbsp;&mdash;&nbsp;Custom synthesis blocks (optional)</h3>
 * The item can override the header or statistics block of its enclosing
 * category / item-category by returning non-{@code null} objects from the
 * four <em>“override”</em> methods.
 * If a method returns {@code null}, the dashboard runtime supplies a default.
 *
 * <h3>3&nbsp;&mdash;&nbsp;Visualisation</h3>
 * <ul>
 *   <li>{@link #templateId()} – logical identifier that the
 *       {@link com.gwngames.core.dashboard.template.TemplateRegistry TemplateRegistry}
 *       maps to an HTML snippet.</li>
 *   <li>{@link #model()} – <strong>class object</strong> of an
 *       {@link IDashboardContent} implementation.
 *       The framework obtains an instance (via its injector or reflection)
 *       and then calls {@link IDashboardContent#templateId()} /
 *       {@link IDashboardContent#model()} for the actual data.</li>
 * </ul>
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD_ITEM, allowMultiple = true)
public non-sealed interface IDashboardItem extends IDashboardNode {

    /* ─────────────────────────── placement ─────────────────────────── */

    Enum<?> tableKey();
    Enum<?> categoryKey();
    Enum<?> itemCategoryKey();

    /* ───────────────── optional synthesis overrides ────────────────── */

    default IDashboardHeader  categoryHeader()      { return null; }
    default IDashboardContent categoryStatistics()  { return null; }
    default IDashboardHeader  itemCategoryHeader()  { return null; }
    default IDashboardContent itemCategoryStats()   { return null; }

    /* ───────────────────── visualisation of the item ───────────────── */

    /** Logical key into {@link com.gwngames.core.dashboard.template.TemplateRegistry}. */
    String templateId();

    /**
     * Implementation <em>class</em> of an {@link IDashboardContent} that knows
     * how to supply the actual numbers/series for the template.
     *
     * @return never {@code null}
     */
    Class<? extends IDashboardContent> model();
}
