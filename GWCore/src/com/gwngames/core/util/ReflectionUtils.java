package com.gwngames.core.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionUtils {

    private ReflectionUtils() {}

    public static void setField(Object target, String fieldName, Object value) {
        if (target == null) throw new IllegalArgumentException("target is null");
        if (fieldName == null || fieldName.isBlank()) throw new IllegalArgumentException("fieldName is blank");

        Class<?> cls = target.getClass();
        Field field = findField(cls, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' not found on " + cls.getName() + " (including superclasses)");
        }

        // 1) Try VarHandle first (often plays nicer with newer JVMs, but still respects module boundaries)
        // If it fails, fall back to classic reflection.
        try {
            setWithVarHandle(target, field, value);
            return;
        } catch (Throwable ignored) {
            // Fall through to reflection
        }

        // 2) Classic reflection fallback
        try {
            if (!field.canAccess(target)) {
                field.setAccessible(true); // may throw InaccessibleObjectException on Java 16+ if not opened
            }

            Object coerced = coerce(value, field.getType());
            field.set(target, coerced);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on " + cls.getName(), e);
        }
    }

    private static void setWithVarHandle(Object target, Field field, Object value) throws Throwable {
        Class<?> declaring = field.getDeclaringClass();

        // You need a lookup that has private access to the declaring class
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());
        VarHandle vh = lookup.unreflectVarHandle(field);

        Object coerced = coerce(value, field.getType());

        // For instance fields
        if (!Modifier.isStatic(field.getModifiers())) {
            vh.set(target, coerced);
        } else {
            // For static fields
            vh.set(coerced);
        }
    }

    public static Field findField(Class<?> cls, String fieldName) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Cannot set primitive " + targetType.getName() + " to null");
            }
            return null;
        }

        // Already assignable
        if (targetType.isInstance(value)) return value;

        // Handle primitives (and their wrappers)
        if (targetType.isPrimitive()) {
            if (targetType == boolean.class) return toBoolean(value);
            if (targetType == char.class) return toChar(value);
            if (targetType == byte.class) return (byte) toLong(value);
            if (targetType == short.class) return (short) toLong(value);
            if (targetType == int.class) return (int) toLong(value);
            if (targetType == long.class) return toLong(value);
            if (targetType == float.class) return (float) toDouble(value);
            if (targetType == double.class) return toDouble(value);
        }

        // Basic Number conversions for boxed numeric types
        if (Number.class.isAssignableFrom(targetType) && value instanceof Number n) {
            if (targetType == Byte.class) return n.byteValue();
            if (targetType == Short.class) return n.shortValue();
            if (targetType == Integer.class) return n.intValue();
            if (targetType == Long.class) return n.longValue();
            if (targetType == Float.class) return n.floatValue();
            if (targetType == Double.class) return n.doubleValue();
        }

        // Enum from String
        if (targetType.isEnum() && value instanceof String s) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object e = Enum.valueOf((Class<? extends Enum>) targetType, s);
            return e;
        }

        // String conversion (optional, handy)
        if (targetType == String.class) return String.valueOf(value);

        throw new IllegalArgumentException(
            "Cannot coerce value of type " + value.getClass().getName() +
                " to " + targetType.getName()
        );
    }

    public static boolean toBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        if (v instanceof Number n) return n.intValue() != 0;
        throw new IllegalArgumentException("Cannot coerce to boolean: " + v.getClass().getName());
    }

    public static char toChar(Object v) {
        if (v instanceof Character c) return c;
        if (v instanceof String s && !s.isEmpty()) return s.charAt(0);
        if (v instanceof Number n) return (char) n.intValue();
        throw new IllegalArgumentException("Cannot coerce to char: " + v.getClass().getName());
    }

    public static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s.trim());
        throw new IllegalArgumentException("Cannot coerce to long: " + v.getClass().getName());
    }

    public static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) return Double.parseDouble(s.trim());
        throw new IllegalArgumentException("Cannot coerce to double: " + v.getClass().getName());
    }
}
