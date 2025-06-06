package com.gwngames.core.event.base;

import com.badlogic.gdx.utils.Array;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IMacroEvent;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE)
public class MacroEvent extends BaseComponent implements IMacroEvent {
    private final String id;
    private final Array<IEvent> events = new Array<>();

    public MacroEvent(String id) { this.id = id; }

    @Override
    public void addEvent(IEvent event) {
        event.setMacroEvent(this);
        events.add(event);
    }

    @Override
    public Array<IEvent> getEvents() { return events; }

    @Override
    public String getId() { return id; }
}
