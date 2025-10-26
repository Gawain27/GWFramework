package com.gwngames.core.event.base;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.event.trigger.IEventTrigger;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.base.BaseComponent;

import java.util.UUID;

/** Simple base class that takes care of id + enabled flag.<p>
* Sub-classes implement the real firing logic.
*/
public abstract class AbstractEventTrigger extends BaseComponent implements IEventTrigger {

    private String id;
    private volatile boolean enabled = true;

    @Inject
    protected IMasterEventQueue masterEventQueue;

    @PostInject
    void init(){
        this.id = "trigger-"+getClass().getSimpleName()+"-"+UUID.randomUUID();
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
