package com.gwngames.starter.base;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Verifies that every concrete component loaded by ModuleClassLoader
 * is connected (i.e. has at least one incoming or outgoing @Inject reference).
 */
public class ComponentGraphConnectivityTest extends BaseTest {

    @Override
    protected void runTest() throws Exception {
        // 1) Grab the singleton ModuleClassLoader
        ModuleClassLoader loader = ModuleClassLoader.getInstance();

        // 2) Reflectively access the private 'concreteTypes' field
        //    which holds all concrete component classes
        Field concreteTypesField = ModuleClassLoader.class.getDeclaredField("concreteTypes");
        concreteTypesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Class<?>> concreteTypes = (List<Class<?>>) concreteTypesField.get(loader);

        // 3) Build a directed graph: for each component C, record an outbound edge
        //    to every field‐type annotated with @Inject inside C.
        Map<Class<?>, Set<Class<?>>> outgoing = new HashMap<>();
        Map<Class<?>, Set<Class<?>>> incoming = new HashMap<>();

        // Initialize maps for every component
        for (Class<?> comp : concreteTypes) {
            outgoing.put(comp, new HashSet<>());
            incoming.put(comp, new HashSet<>());
        }

        // Walk through each concrete component class
        for (Class<?> comp : concreteTypes) {
            // Look at every declared field in 'comp'
            for (Field f : comp.getDeclaredFields()) {
                if (f.getAnnotation(Inject.class) == null) {
                    continue;
                }
                Class<?> depType = f.getType();

                // Only consider it an edge if depType is one of our concrete components
                if (outgoing.containsKey(depType)) {
                    // record comp --> depType
                    outgoing.get(comp).add(depType);
                    incoming.get(depType).add(comp);
                }
            }

            // In some rare setups, components might also receive dependencies
            // through setter methods (unlikely here), but if you do use setter‐injection,
            // you could walk all methods named "setXxx" and check @Inject on them.
            // For now we only inspect field‐level @Inject.
        }

        // 4) Finally, assert that no component is “isolated”
        //    i.e. for every class C: (incoming.get(C).size() + outgoing.get(C).size()) > 0
        List<String> isolated = new ArrayList<>();
        for (Class<?> comp : concreteTypes) {
            int inDegree = incoming.get(comp).size();
            int outDegree = outgoing.get(comp).size();
            if (inDegree + outDegree == 0) {
                isolated.add(comp.getName());
            }
        }

        // If we found any isolated components, fail the test and list them
        if (!isolated.isEmpty()) {
            StringBuilder msg = new StringBuilder("Found isolated components (no @Inject incoming or outgoing):\n");
            for (String name : isolated) {
                msg.append("  • ").append(name).append("\n");
            }
            Assertions.fail(msg.toString());
        }
    }
}
