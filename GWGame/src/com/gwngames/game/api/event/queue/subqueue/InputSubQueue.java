package com.gwngames.game.api.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.api.ex.UnknownEventException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.core.event.queue.ConcurrentSubQueue;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.event.input.*;
import com.gwngames.game.api.input.IInputListener;

@Init(module = GameModule.GAME, subComp = GameSubComponent.INPUT_QUEUE)
public class InputSubQueue extends ConcurrentSubQueue<IInputEvent> {
    FileLogger log = FileLogger.get(LogFiles.INPUT);

    @PostInject
    @Override
    protected void init(){
        this.maxParallel = config.get(EventParameters.INPUT_EVENT_MAX_THREAD);
        super.init();
    }
    @Override
    protected void processEvent(IInputEvent ev) throws EventException {
        if (ev == null){
            log.error("null input event registered");
            return;
        }

        switch (ev) {
            case IAxisEvent iAxisEvent -> listenOn(iAxisEvent);
            case IButtonEvent iButtonEvent -> listenOn(iButtonEvent);
            case ITouchEvent iTouchEvent -> listenOn(iTouchEvent);
            case IInputActionEvent iInputActionEvent -> {
                if (ev.getAssignedAction() != null) {
                    log.debug("Executing action: {}", ev.getClass().getSimpleName());
                    ev.getAssignedAction().execute(ev);
                }
            }
            default -> throw new UnknownEventException(ev);
        }
    }

    private void listenOn(IInputEvent event){
        for (IInputListener listener : event.getAdapter().getListeners()){

            log.debug("\nSending input: {}\nfrom: {}\nto: {}\n",
                event,
                event.getAdapter(),
                listener.identity());
            listener.onInput(event);
        }
    }

    @Override
    public Class<IInputEvent> getType() {
        return IInputEvent.class;
    }
}
