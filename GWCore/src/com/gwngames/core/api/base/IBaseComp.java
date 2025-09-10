package com.gwngames.core.api.base;

import com.gwngames.core.util.ComponentUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Root marker for every framework component (including enums).
 */
public interface IBaseComp {
    /** mult-ids for enum constants (populated by ModuleClassLoader via setMultId). */
    Map<IBaseComp, Integer> ENUM_IDS = new ConcurrentHashMap<>();

    /** Usual call site: never assigns as a fallback. */
    default int getMultId() { return getMultId(false); }

    /**
     * @param assignIfMissing if true and this is an enum whose id wasn't assigned yet,
     *                        assign one now via {@link ComponentUtils#assignEnum(IBaseComp)}.
     */
    default int getMultId(boolean assignIfMissing){
        // Enums: id is injected by the loader (or on-demand if explicitly allowed)
        if (getClass().isEnum()) {
            Integer id = ENUM_IDS.get(this);
            if (id == null) {
                if (assignIfMissing) {
                    int newId = ComponentUtils.assignEnum(this);
                    ENUM_IDS.put(this, newId);
                    return newId;
                }
                throw new IllegalStateException(
                    "Enum multId not set for " + getClass().getSimpleName()
                        + " – ensure ModuleClassLoader has been initialised");
            }
            return id;
        }

        // Normal components: reflectively read BaseComponent's multId field
        try {
            Class<?> cur = getClass();
            while (cur != null && cur != Object.class) {
                try {
                    Field f = cur.getDeclaredField("multId");
                    f.setAccessible(true);
                    int id = (int) f.get(this);
                    f.setAccessible(false);
                    return id;
                } catch (NoSuchFieldException ignored) {
                    cur = cur.getSuperclass();
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access multId on " + getClass().getSimpleName(), e);
        }
        throw new IllegalStateException("No multId field found on " + getClass().getSimpleName()
            + " – did you extend BaseComponent?");
    }

    /** Loader/Test path to set enum ids. Has no effect for normal components. */
    default void setMultId(int newId) {
        if (getClass().isEnum()) {
            ENUM_IDS.put(this, newId);
        }
        // Non-enums ignore: BaseComponent assigns at construction time.
    }

    private void ensureEnum() {
        if (!this.getClass().isEnum()) {
            throw new UnsupportedOperationException(
                "This method is only supported for enums: " + this.getClass().getName()
            );
        }
    }

    default String dashboardTitle() {
        ensureEnum();
        return this.toString();
    }

    default String safeId() {
        ensureEnum();
        return this.toString();
    }

    default int errorCount() {
        ensureEnum();
        return 0;
    }

    default String dashboardKey() {
        ensureEnum();
        return this.toString();
    }

}
