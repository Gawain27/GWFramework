package com.gwngames.core.api.base.monitor;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.build.Init;

import java.io.InputStream;
import java.util.List;

/**
 * The <strong>root</strong> of the in-memory dashboard model.  Typically the
 * concrete implementation also hosts the HTTP endpoint and renders the page,
 * but this interface is kept minimal so tests can replace it with a stub.
 */
@Init(module = DefaultModule.INTERFACE, component = CoreComponent.DASHBOARD)
public non-sealed interface IDashboard extends IDashboardNode {

    void maybeStart();

    void maybeStart(int port);

    void shutdown();

    InputStream renderBoard();

    String prepareCss();
}
