package com.gwngames.core.util;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Queue;

import java.util.Collection;
import java.util.Optional;

public final class CollectionUtils {

    /**
     * Creates a new {@link Array} that contains the elements of the given {@link Queue},
     * without modifying the queue’s internal state.
     *
     * @param queue the source queue (must not be {@code null})
     * @param <T>   the element type
     * @return a new ordered {@link Array} with the queue’s current contents
     */
    public static <T> Array<T> queueToArray (Queue<T> queue) {
        // - Pass 'true' for an _ordered_ Array and pre-size it for efficiency
        Array<T> result = new Array<>(true, queue.size);

        // Queue#get(int) is O(1) and **does not** remove the element
        for (int i = 0; i < queue.size; i++) {
            result.add(queue.get(i));
        }

        return result;
    }

    public static boolean allEqual(Collection<?> list) {
        if (list == null || list.isEmpty()) return true;
        Optional<?> firstOpt = list.stream().findFirst();
        Object first = firstOpt.get();
        for (Object element : list) {
            if (!first.equals(element)) return false;
        }
        return true;
    }

}
