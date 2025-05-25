package com.gwngames.core.api.base;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.SubComponentNames;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base component interface, exposes method for component lookup
 * */
public interface IBaseComp {
    // id generator, key is iface.simplename + comp.simplename
    Map<String, AtomicInteger> idMap = new HashMap<>();
    Map<Class<? extends IBaseComp>, Map<SubComponentNames, Integer>> multIdMap = new HashMap<>();

    @SuppressWarnings("unchecked")
    default Integer getMultId(){
        Init initAnn = getClass().getAnnotation(Init.class);
        Optional<Class<?>> iFaceOpt = Arrays.stream(getClass().getInterfaces()).filter(clazz -> clazz.getAnnotation(Init.class) != null).findFirst();
        if (iFaceOpt.isPresent()){
            Init iFaceInit = iFaceOpt.get().getAnnotation(Init.class);
            if (iFaceInit.allowMultiple()){
                Map<SubComponentNames, Integer> compMap = multIdMap.get(iFaceOpt.get());
                if (compMap == null){
                    compMap = new HashMap<>();
                    multIdMap.put((Class<? extends IBaseComp>) iFaceOpt.get(), compMap);
                    return computeNextMultId(iFaceInit,initAnn,compMap);
                }
                return computeNextMultId(iFaceInit,initAnn,compMap);
            }
            return null; // do not store.
        }
        throw new IllegalStateException("Invalid mult id requested: " + getClass().getSimpleName());
    }

    default Integer computeNextMultId(Init iFaceInit, Init initAnn, Map<SubComponentNames, Integer> compMap){
        String idKey = iFaceInit.getClass().getSimpleName()+getClass().getSimpleName();
        AtomicInteger generator = idMap.computeIfAbsent(idKey, k -> new AtomicInteger(0));
        Integer id = generator.addAndGet(1);
        compMap.put(initAnn.subComp(), id);
        return id;
    }
}
