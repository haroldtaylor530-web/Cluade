package com.harold.corpsedecay.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Active only while a corpse is being drawn. Mixins read it to pose the limbs (in every model
 * drawn on the body: armour, clothes, fur...) and to swap in the decay texture. Whatever it
 * changes on shared model parts is put back when the corpse is done drawing, so live mobs that
 * use the same model are never affected.
 */
public final class RenderContext {
    public static RenderContext current;

    public final LivingEntity puppet;
    private final RenderType renderType;
    private final Map<String, float[]> poses = new HashMap<>();
    private final Map<ModelPart, float[]> saved = new IdentityHashMap<>();

    public RenderContext(LivingEntity puppet, RenderType renderType) {
        this.puppet = puppet;
        this.renderType = renderType;
    }

    public void pose(String name, float x, float y, float z) {
        poses.put(name, new float[] {x, y, z});
    }

    public RenderType renderType() {
        return renderType;
    }

    /** Called from ModelPart.translateAndRotate. */
    public void apply(ModelPart part, String name) {
        if (name == null) return;
        float[] r = poses.get(name);
        if (r == null) return;
        saved.computeIfAbsent(part, p -> new float[] {p.xRot, p.yRot, p.zRot});
        part.xRot = r[0];
        part.yRot = r[1];
        part.zRot = r[2];
    }

    public void begin() {
        current = this;
    }

    public void end() {
        current = null;
        for (Map.Entry<ModelPart, float[]> e : saved.entrySet()) {
            ModelPart p = e.getKey();
            float[] r = e.getValue();
            p.xRot = r[0];
            p.yRot = r[1];
            p.zRot = r[2];
        }
        saved.clear();
    }
}
