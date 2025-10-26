package com.gwngames.core.api.base.monitor;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.io.InputStream;
import java.util.List;

/**
 * The <strong>root</strong> of the in-memory dashboard model.  Typically the
 * concrete implementation also hosts the HTTP endpoint and renders the page,
 * but this interface is kept minimal so tests can replace it with a stub.
 */
@Init(module = ModuleNames.INTERFACE, component = ComponentNames.DASHBOARD)
public non-sealed interface IDashboard extends IDashboardNode {

    void maybeStart();

    void maybeStart(int port);

    void shutdown();

    InputStream renderBoard();
}
