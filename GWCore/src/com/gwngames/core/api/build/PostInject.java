package com.gwngames.core.api.build;

import java.lang.annotation.*;

/**
 * Methods marked with this annotation are executed after injection of the overriding object
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostInject {

}
