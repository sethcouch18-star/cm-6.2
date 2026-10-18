package com.example.colormons;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Quaternionf;

/**
 * Draws the real Pokemon inside the GUI. No auto-rotation: the caller passes the
 * yaw and pitch it has accumulated from dragging.
 */
public class Preview {

    public static void draw(GuiGraphics g, PokemonEntity entity, float x, float y, float size,
                            float yaw, float pitch) {
        if (entity == null) return;
        Minecraft mc = Minecraft.getInstance();

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 100.0f);
        pose.scale(size, size, -size);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        pose.mulPose(new Quaternionf().rotateY(yaw));
        if (pitch != 0f) pose.mulPose(new Quaternionf().rotateX(pitch));

        Lighting.setupForEntityInInventory();
        mc.getEntityRenderDispatcher().setRenderShadow(false);
        try {
            mc.getEntityRenderDispatcher().render(
                    entity,
                    0.0, 0.0, 0.0,
                    0.0f,
                    0.0f,
                    pose,
                    mc.renderBuffers().bufferSource(),
                    0xF000F0);
            mc.renderBuffers().bufferSource().endBatch();
        } finally {
            mc.getEntityRenderDispatcher().setRenderShadow(true);
            pose.popPose();
        }
    }
}
