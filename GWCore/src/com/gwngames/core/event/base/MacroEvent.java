package com.gwngames.core.event.base;

import com.badlogic.gdx.utils.Array;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IMacroEvent;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.StringUtils;

@Init(module = ModuleNames.CORE)
public class MacroEvent extends BaseComponent implements IMacroEvent {
    private String id;
    private final Array<IEvent> events = new Array<>();

    @Override
    public void addEvent(IEvent event) {
        event.setMacroEvent(this);
        events.add(event);
    }

    @Override
    public Array<IEvent> getEvents() { return events; }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        if (!StringUtils.isEmpty(this.id)) return;
        if (StringUtils.isEmpty(id))
            throw new IllegalArgumentException("id cannot be empty");
        this.id = id;
    }
}
