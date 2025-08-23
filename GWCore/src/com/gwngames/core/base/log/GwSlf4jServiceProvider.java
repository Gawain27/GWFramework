package com.gwngames.core.base.log;


import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPMDCAdapter;

public final class GwSlf4jServiceProvider implements SLF4JServiceProvider {
    public static final String REQUESTED_API_VERSION = "2.0.99"; // as required by SLF4J
    private final ILoggerFactory loggerFactory = new GwLoggerFactory();
    private final IMarkerFactory markerFactory = new BasicMarkerFactory();
    private final MDCAdapter mdcAdapter = new NOPMDCAdapter();

    @Override public void initialize() { /* no-op */ }
    @Override public ILoggerFactory getLoggerFactory() { return loggerFactory; }
    @Override public IMarkerFactory getMarkerFactory() { return markerFactory; }
    @Override public MDCAdapter getMDCAdapter() { return mdcAdapter; }
    @Override public String getRequestedApiVersion() { return REQUESTED_API_VERSION; }
}
