package com.gwngames.core.event.cond.base;

import com.gwngames.core.api.event.IExecutionCondition;

import java.util.concurrent.atomic.AtomicInteger;

public final class GlobalRule {
    final String               id;
    final IExecutionCondition condition;
    volatile boolean           enabled;
    final AtomicInteger vetoCount = new AtomicInteger();

    public GlobalRule(String id, IExecutionCondition c, boolean enabled) {
        this.id = id;
        this.condition = c;
        this.enabled = enabled;
    }

    public AtomicInteger getVetoCount() { return vetoCount; }

    public IExecutionCondition getCondition() { return condition; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
