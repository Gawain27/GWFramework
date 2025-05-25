package com.gwngames.core.api.ex;


import com.gwngames.core.util.ClassUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: this is trash, replace with defined values.
public class ExceptionCode {
    static Integer DECA = 0; // critical exception
    static Integer HUNDRED = 1; // core exception
    static Integer THOUSAND = 2; // system exception
    static Integer TENS_THOUSANDS = 3; // game exception
    static Integer HUNDRED_THOUSANDS = 4; // project exception
    static Integer MILLION = 5; // other exceptions

    private static final Map<Integer, Class<? extends BaseException>> exceptionMap = new HashMap<>();
    private static final Map<Class<? extends BaseException>, Integer> classToCodeMap = new HashMap<>();

    private static final Map<Integer, Integer> PLACE_VALUES = Map.of(
        DECA, 10,
        HUNDRED, 100,
        THOUSAND, 1_000,
        TENS_THOUSANDS, 10_000,
        HUNDRED_THOUSANDS, 100_000,
        MILLION, 1_000_000
    );

    private static Integer counter = 10_000_000; // Start high to avoid collisions

    public static <T extends BaseException> Integer generate(Class<T> exceptionClass) {
        if (classToCodeMap.containsKey(exceptionClass)) {
            return classToCodeMap.get(exceptionClass);
        }

        Integer code = counter++;
        try {
            exceptionMap.put(code, exceptionClass);
            classToCodeMap.put(exceptionClass, code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create exception instance", e);
        }

        return code;
    }

    public static <T extends BaseException> Integer generate(Class<T> exceptionClass, List<Integer> codeClass) {
        if (classToCodeMap.containsKey(exceptionClass)) {
            return classToCodeMap.get(exceptionClass);
        }

        Integer code = 0;
        for (Integer level : codeClass) {
            if (!PLACE_VALUES.containsKey(level)) {
                throw new IllegalArgumentException("Invalid code class level: " + level);
            }
            code += PLACE_VALUES.get(level);
        }

        // Add a small hashed offset for uniqueness in same bucket, but avoid over clustering!
        code += exceptionClass.getSimpleName().hashCode() % 10;

        try {
            exceptionMap.put(code, exceptionClass);
            classToCodeMap.put(exceptionClass, code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create exception instance", e);
        }

        return code;
    }

    public static <T extends BaseException> Integer getExceptionCode(Class<T> exceptionClass) {
        return classToCodeMap.get(exceptionClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BaseException> Class<T> getExceptionCode(Integer exceptionCode, Class<T> castTo) {
        Class<? extends BaseException> baseException = exceptionMap.get(exceptionCode);
        if (baseException == null) {
            throw new IllegalArgumentException("Exception code not found.");
        }

        if (!castTo.isAssignableFrom(baseException)) {
            throw new IllegalArgumentException("Invalid exception code or cast type.");
        }

        return (Class<T>) baseException ;
    }
}

