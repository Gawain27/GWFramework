package com.gwngames.core.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputChainManager;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.input.InputContext;

import java.util.*;

@Init(module = ModuleNames.CORE)
public class FastInputChainManager extends BaseComponent implements IInputChainManager {

    private static final long SEED = 11_400_714_819_323_198L;
    private static final long BASE = 1469598103934665603L;

    private record Rec(IInputChain chain, boolean enabled) {}
    private final Map<String, Rec> byName = new HashMap<>();

    // length -> (hash -> list of chains)
    private final Map<Integer, Map<Long, List<IInputChain>>> indexByLen = new HashMap<>();

    @Override
    public synchronized void register(IInputChain c, boolean enabled) {
        byName.put(c.name(), new Rec(c, enabled));
        int len = c.combos().size();
        long h = hashCombos(c.combos());
        indexByLen.computeIfAbsent(len, k -> new HashMap<>())
            .computeIfAbsent(h, k -> new ArrayList<>())
            .add(c);
    }

    @Override
    public synchronized void enable(String n)  { byName.computeIfPresent(n,(k,r)->new Rec(r.chain,true)); }
    @Override
    public synchronized void disable(String n) { byName.computeIfPresent(n,(k,r)->new Rec(r.chain,false)); }

    @Override
    public Optional<IInputChain> match(List<? extends IInputCombo> buffer, InputContext ctx) {
        if (buffer.isEmpty()) return Optional.empty();

        List<Integer> lengths = new ArrayList<>(indexByLen.keySet());
        lengths.sort(Comparator.reverseOrder()); // longest first

        for (int len : lengths) {
            if (buffer.size() < len) continue;

            long headHash = hashFirst(buffer, len);
            List<IInputChain> candidates = indexByLen.get(len).get(headHash);
            if (candidates == null || candidates.isEmpty()) continue;

            for (IInputChain c : candidates) {
                Rec rec = byName.get(c.name());
                if (rec == null || !rec.enabled) continue;
                if (!c.visibility().isEmpty() && !c.visibility().contains(ctx)) continue;
                if (equalsHead(buffer, c.combos())) return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private static boolean equalsHead(List<? extends IInputCombo> buf, List<IInputCombo> chain) {
        if (buf.size() < chain.size()) return false;
        for (int i = 0; i < chain.size(); i++) {
            if (!buf.get(i).identifiers().equals(chain.get(i).identifiers())) return false;
        }
        return true;
    }

    private static long hashFirst(List<? extends IInputCombo> buf, int len) {
        long h = BASE;
        for (int i = 0; i < len; i++) h = mix(h, buf.get(i));
        return h;
    }
    private static long hashCombos(List<IInputCombo> combos) {
        long h = BASE;
        for (IInputCombo c : combos) h = mix(h, c);
        return h;
    }
    private static long mix(long h, IInputCombo c) {
        long part = 31L * c.getMultId() + c.identifiers().hashCode();
        h ^= part + SEED + (h << 6) + (h >> 2);
        return h;
    }
}
