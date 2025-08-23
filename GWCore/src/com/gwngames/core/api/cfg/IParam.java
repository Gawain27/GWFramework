package com.gwngames.core.api.cfg;

/**
 * Public interface to expose only relevant information about parameters<br>
 * DO NOT USE ParamKey arbitrarily, resort to IParam, if possible
 * */
public interface IParam<T> {
    String key();
    Class<T> type();
    boolean userModifiable();   // default: false
    boolean nullable();         // default: false
}
