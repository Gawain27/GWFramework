package com.gwngames.game.data.render;

import com.gwngames.game.api.render.IRenderPhase;

/**
 * High-level rendering buckets. The composer uses these to produce a deterministic order.
 * <p>
 * WORLD: world geometry/sprites<br>
 * UI: screen-space UI<br>
 * DEBUG: overlays, debug primitives<br>
 */
public enum RenderPhase implements IRenderPhase {
    WORLD,
    UI,
    DEBUG
}
