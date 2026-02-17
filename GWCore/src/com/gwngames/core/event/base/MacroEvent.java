package com.gwngames.core.event.base;

import com.gwngames.core.CoreModule;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IMacroEvent;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Init(module = CoreModule.CORE)
public class MacroEvent extends BaseComponent implements IMacroEvent {
    private String id;
    private final List<IEvent> events = new ArrayList<>();

    @Override
    public void addEvent(IEvent event) {
        event.setMacroEvent(this);
        events.add(event);
    }

    @Override
    public List<IEvent> getEvents() { return events; }

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
