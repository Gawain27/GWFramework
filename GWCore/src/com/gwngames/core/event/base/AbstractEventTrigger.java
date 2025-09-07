package com.gwngames.core.event.base;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IEventTrigger;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.base.BaseComponent;

import java.util.UUID;

/** Simple base class that takes care of id + enabled flag.<p>
* Sub-classes implement the real firing logic.
*/
public abstract class AbstractEventTrigger extends BaseComponent implements IEventTrigger {

    private final String id;
    private volatile boolean enabled = true;

    @Inject
    protected IMasterEventQueue masterEventQueue;

    protected AbstractEventTrigger() {
        this.id = "trigger-"+getClass().getSimpleName()+"-"+UUID.randomUUID().toString();
    }

    @Override
    public final String getId() {
        return id;
    }
    @Override
    public final boolean isEnabled() {
        return enabled;
    }
    @Override
    public final void setEnabled(boolean e) {
        enabled = e;
    }
}
