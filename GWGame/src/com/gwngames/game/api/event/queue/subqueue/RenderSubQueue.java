package com.gwngames.game.api.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.core.event.queue.ConcurrentSubQueue;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.event.render.IRenderEvent;

@Init(module = GameModule.GAME, subComp = GameSubComponent.RENDER_QUEUE)
public class RenderSubQueue extends ConcurrentSubQueue<IRenderEvent> {
    @PostInject
    @Override
    protected void init(){
        this.maxParallel = config.get(EventParameters.RENDER_EVENT_MAX_THREAD);
        super.init();
    }
    @Override
    protected void processEvent(IRenderEvent ev) throws EventException {
        // TODO
    }

    @Override
    public Class<IRenderEvent> getType() {
        return IRenderEvent.class;
    }
}
