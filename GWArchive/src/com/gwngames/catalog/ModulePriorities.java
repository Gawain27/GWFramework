package com.gwngames.catalog;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModulePriorities {
    Entry[] value();

    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface Entry {
        String id();
        int priority();
    }
}
