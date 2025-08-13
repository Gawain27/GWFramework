package com.gwngames.core.api.cfg;

public interface IParam<T> {
    String key();
    Class<T> type();
    boolean userModifiable();   // default: false
    boolean nullable();         // default: false
}
