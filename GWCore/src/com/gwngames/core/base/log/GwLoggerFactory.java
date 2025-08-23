package com.gwngames.core.base.log;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

final class GwLoggerFactory implements ILoggerFactory {
    private final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();
    @Override public Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, GwLogger::new);
    }
}
