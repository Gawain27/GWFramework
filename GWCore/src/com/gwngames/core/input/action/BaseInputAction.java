package com.gwngames.core.input.action;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe base implementation of {@link IInputAction} with
 * <strong>per-adapter-slot</strong> enable/disable and cool-down settings.
 *
 * <p>Usage pattern</p>
 * <pre>{@code
 * JumpAction jump = new JumpAction(player);
 * jump.setCooldown(300); // default template 300 ms
 * jump.setEnabled(2, false); // disable for slot #2 only
 * }</pre>
 */
public abstract class BaseInputAction extends BaseComponent implements IInputAction {

    private static final FileLogger log = FileLogger.get(LogFiles.INPUT);

    /* ───────────────────────── per-slot record ───────────────────────── */

    private static final class SlotCfg {
        volatile boolean enabled;
        volatile long coolMs;
        volatile long lastExec;  // epoch millis
        SlotCfg(boolean en,long cd){enabled=en;coolMs=cd;}
    }

    /* default template applied to unseen slots */
    private volatile boolean defEnabled = true;
    private volatile long    defCoolMs  = 0;

    private final Map<Integer,SlotCfg> cfgBySlot = new ConcurrentHashMap<>();

    /* ───────────────────────── public API ────────────────────────────── */

    /* ----- defaults (affect new slots) ----- */
    public void setEnabled(boolean enabled){ this.defEnabled = enabled;          }
    public void setCooldown(long ms)      { this.defCoolMs  = Math.max(0, ms);  }
    public boolean isEnabledDefault()     { return defEnabled; }
    public long    getCooldownDefault()   { return defCoolMs;  }

    /* ----- per-slot overrides ----- */
    public void setEnabled(int slot, boolean enabled){
        cfg(slot).enabled = enabled;
    }
    public void setCooldown(int slot, long ms){
        cfg(slot).coolMs = Math.max(0, ms);
    }
    public boolean isEnabled(int slot){ return cfg(slot).enabled; }
    public long    getCooldown(int slot){ return cfg(slot).coolMs; }

    /* remove override → fall back to defaults */
    public void clearSlotOverride(int slot){
        cfgBySlot.remove(slot);
    }

    /* ───────────────────────── execution ─────────────────────────────── */

    @Override
    public final void execute(IInputEvent evt){
        if (evt == null){
            log.error("No event received");
            return;
        }

        int slot = evt.getSlot();
        SlotCfg c = cfg(slot);

        if (!c.enabled) return;

        long now = System.currentTimeMillis();
        if (c.coolMs > 0 && (now - c.lastExec) < c.coolMs) return;

        try {
            perform(evt);
            c.lastExec = now;
        } catch (Exception ex){
            log.error("Action "+getClass().getSimpleName()+" threw", ex);
        }
    }

    /* ───────────────────────── subclass hook ─────────────────────────── */

    /** Implement your actual behaviour here (already guarded). */
    protected abstract void perform(IInputEvent evt) throws Exception;

    /* ───────────────────────── helpers ───────────────────────────────── */

    private SlotCfg cfg(int slot){
        return cfgBySlot.computeIfAbsent(slot, s -> new SlotCfg(defEnabled, defCoolMs));
    }
}
