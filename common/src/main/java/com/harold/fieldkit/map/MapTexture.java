package com.harold.fieldkit.map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** A texture filled from an ARGB array, drawn on screen. */
public final class MapTexture {
    private final int w, h;
    private final DynamicTexture tex;
    private final ResourceLocation id;

    public MapTexture(String name, int w, int h) {
        this.w = w;
        this.h = h;
        this.tex = new DynamicTexture(new NativeImage(w, h, false));
        this.id = ResourceLocation.tryBuild("fieldkit", "dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(id, tex);
    }

    public void upload(int[] argb) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) img.setPixelRGBA(x, y, BiomeColors.abgr(argb[y * w + x]));
        tex.upload();
    }

    public void draw(GuiGraphics g, int x, int y, int dw, int dh) {
        g.blit(id, x, y, dw, dh, 0, 0, w, h, w, h);
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }
}
