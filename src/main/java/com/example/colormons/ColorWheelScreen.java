package com.example.colormons;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ColorWheelScreen extends Screen {
    private static final int WHEEL = 150;
    private static final int LEFT_W = 150;
    private static final int RIGHT_W = 196;
    private static final int ROW_H = 24;

    private static ResourceLocation wheelTex;

    private final UUID monId;
    private final String species;
    private final PokemonEntity entity;
    private final MonColors mc;
    private final List<int[]> history = new ArrayList<>();

    private int selected = 0;
    private float brightness = 1f;
    private boolean editing = false;
    private int hovered = -1;
    private boolean hasTexture = true;
    private int paintPanelSize = 150;

    // preview orientation: changed only by dragging
    private float yaw = 0.6f;
    private float pitch = -0.25f;
    private boolean draggingPreview = false;

    private int wheelX, wheelY, barVX, barSX;
    private int groupX, groupY, swatchX;
    private int mapX, mapY, mapSize;
    private int previewX, previewY, previewW, previewH;
    private int paintX, paintY;
    private int actX, actY, finX, finY;
    private int brushX, brushY;

    public ColorWheelScreen(PokemonEntity mon) {
        super(Component.literal(mon.getPokemon().getSpecies().getName() + " Colours"));
        this.entity = mon;
        this.monId = mon.getPokemon().getUuid();
        this.species = mon.getPokemon().getSpecies().getResourceIdentifier().getPath();
        this.mc = ColorStore.of(monId);

        // Custom fix: give every group a colour to start from, so painting into any of
        // them renders. Groups the player has already chosen keep their colour.
        for (int i = 0; i < MonColors.GROUPS; i++) {
            if (mc.colors[i] < 0) mc.colors[i] = Palette.FALLBACK[i];
        }

        for (int i = 0; i < MonColors.GROUPS; i++) {
            if (mc.colors[i] >= 0) { selected = i; break; }
        }
        ScreenData.groups(species);
        this.hasTexture = ScreenData.available(species);
    }

    private void snapshot() {
        history.add(mc.colors.clone());
        if (history.size() > 20) history.remove(0);
    }

    @Override
    protected void init() {
        buildWheelTexture();

        int rightX = width - RIGHT_W - 12;
        groupX = rightX;
        groupY = 44;
        swatchX = rightX + 132;

        wheelX = 220;
        wheelY = 60;
        barVX = wheelX + WHEEL + 12;
        barSX = wheelX + WHEEL + 36;

        // pixel map: TOP LEFT, beside the wheel
        mapX = 20;
        mapY = 44;
        mapSize = LEFT_W;

        // 3D preview: BOTTOM LEFT, wide panel under the map
        previewX = 20;
        previewY = height - 200;
        previewW = LEFT_W + 96;
        previewH = 144;

        // paint grid: pinned to the very top-left corner while editing
        paintX = 8;
        paintY = 8;
        paintPanelSize = PaintPanel.sizeFor(species, 280);

        brushX = rightX;
        brushY = 44 + MonColors.GROUPS * ROW_H + 32;

        actX = 16;
        actY = height - 34;
        finX = 16;
        finY = height - 34;

        for (int i = 0; i < MonColors.GROUPS; i++) {
            final int g = i;
            addRenderableWidget(Button.builder(Component.literal(Palette.GROUP_NAMES[i]), b -> selected = g)
                    .bounds(groupX, groupY + i * ROW_H, 126, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal(editing ? "Edit: ON" : "Edit: OFF"), b -> {
            editing = !editing;
            b.setMessage(Component.literal(editing ? "Edit: ON" : "Edit: OFF"));
            paintPanelSize = PaintPanel.sizeFor(species, 280);
        }).bounds(brushX, brushY, 88, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(mc.emissive ? "Glow: ON" : "Glow: OFF"), b -> {
            mc.emissive = !mc.emissive;
            b.setMessage(Component.literal(mc.emissive ? "Glow: ON" : "Glow: OFF"));
            ColorStore.touched(monId);
        }).bounds(brushX, brushY + 48, 88, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Brush " + (EditBrush.size + 1)), b -> {
            EditBrush.size = (EditBrush.size + 1) % 4;
            b.setMessage(Component.literal("Brush " + (EditBrush.size + 1)));
        }).bounds(brushX + 92, brushY, 88, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Undo brush"), b -> {
            EditBrush.undo(mc, (EditBrush.size + 1) * (EditBrush.size + 1));
            ColorStore.touched(monId);
        }).bounds(brushX, brushY + 24, 180, 20).build());

        // "Reset view" sits with the brush controls so the preview is easy to straighten
        addRenderableWidget(Button.builder(Component.literal("Reset view"), b -> {
            yaw = 0.6f;
            pitch = -0.25f;
        }).bounds(brushX + 92, brushY + 48, 88, 20).build());

        for (int i = 0; i < 4; i++) {
            final int f = i;
            addRenderableWidget(Button.builder(Component.literal(Palette.FINISH_NAMES[i]), b -> {
                snapshot();
                mc.finish = f;
                ColorStore.touched(monId);
            }).bounds(finX + 372 + i * 92, finY, 88, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> snapshot())
                .bounds(actX, actY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Undo"), b -> {
            if (!history.isEmpty()) {
                System.arraycopy(history.remove(history.size() - 1), 0, mc.colors, 0, MonColors.GROUPS);
                ColorStore.touched(monId);
            }
        }).bounds(actX + 92, actY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            snapshot();
            for (int i = 0; i < MonColors.GROUPS; i++) mc.colors[i] = Palette.FALLBACK[i];
            mc.editPixels.clear();
            mc.editGroups.clear();
            mc.finish = 0;
            ColorStore.touched(monId);
        }).bounds(actX + 184, actY, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(actX + 276, actY, 88, 20).build());
    }

    private void buildWheelTexture() {
        if (wheelTex != null) return;
        NativeImage img = new NativeImage(WHEEL, WHEEL, true);
        float r = WHEEL / 2f;
        for (int y = 0; y < WHEEL; y++) for (int x = 0; x < WHEEL; x++) {
            float dx = x - r, dy = y - r;
            float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
            if (d > 1f) { img.setPixelRGBA(x, y, 0); continue; }
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            int rgb = Color.HSBtoRGB(hue, d, 1f);
            int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
            img.setPixelRGBA(x, y, 0xFF000000 | (bb << 16) | (gg << 8) | rr);
        }
        wheelTex = minecraft.getTextureManager().register("colormons/wheel", new DynamicTexture(img));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        hovered = groupUnderMouse(mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);

        // ---- TOP LEFT: pixel map (appears while not editing) --------------------
        if (!editing) {
            g.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xCC000000);
            g.drawString(font, hovered >= 0 ? Palette.GROUP_NAMES[hovered] : "all groups",
                    mapX, mapY - 12, hovered >= 0 ? 0xFFFF66 : 0x999999);
            GroupOverlay.drawMap(g, species, mapX, mapY, mapSize, mc, hovered);
            g.drawString(font, "(press Edit to paint)", mapX, mapY + mapSize + 6, 0x666666);
        } else {
            // ---- while editing: the paintable texture, pinned to the corner ------
            int ps = paintPanelSize;
            g.fill(paintX - 2, paintY - 2, paintX + ps + 4, paintY + ps + 4, 0xCC000000);
            g.drawString(font, "click pixels to paint " + Palette.GROUP_NAMES[selected],
                    paintX + 4, paintY + ps + 16, 0x66FF66);
            g.drawString(font, "brush " + (EditBrush.size + 1) + "  |  pick a group on the right",
                    paintX + 4, paintY + ps + 28, 0x999999);
            PaintPanel.draw(g, species, mc, paintX, paintY, 280);
        }

        // ---- BOTTOM LEFT: the 3D preview ---------------------------------------
        g.fill(previewX - 2, previewY - 2, previewX + previewW + 2, previewY + previewH + 2, 0xCC000000);
        String dragHint = "drag to turn";
        g.drawString(font, species, previewX + 4, previewY + 12, 0xAAAAAA);
        g.drawString(font, dragHint,
                previewX + previewW - font.width(dragHint) - 4, previewY + 12, 0x6a6a6a);
        if (entity != null) {
            try {
                Preview.draw(g, entity,
                        previewX + previewW / 2.0f,
                        previewY + previewH - 26.0f,
                        previewH / 2.6f,
                        yaw, pitch);
            } catch (Throwable ignored) {
                g.drawCenteredString(font, "preview unavailable",
                        previewX + previewW / 2, previewY + previewH / 2, 0xAA6666);
            }
        }

        // ---- wheel + bars ------------------------------------------------------
        if (wheelTex != null) g.blit(wheelTex, wheelX, wheelY, 0, 0, WHEEL, WHEEL, WHEEL, WHEEL);
        drawBar(g, barVX, brightness, 0xFFFF4444);
        drawBar(g, barSX, saturation(), 0xFF44FF44);
        g.drawString(font, "brightness", barVX - 16, wheelY - 12, 0xFFFF8888);
        g.drawString(font, "saturation", barSX - 10, wheelY + WHEEL + 4, 0xFF88FF88);

        // ---- group rows --------------------------------------------------------
        for (int i = 0; i < MonColors.GROUPS; i++) {
            int y = groupY + i * ROW_H;
            int n = countPixels(i);
            boolean chosen = mc.colors[i] >= 0;
            if (i == selected) g.fill(groupX - 3, y - 2, groupX + 172, y + 22, 0x55FFFFFF);
            if (i == hovered && i != selected) g.fill(groupX - 3, y - 2, groupX + 172, y + 22, 0x22FFFF00);
            String cnt = n + " px";
            g.drawString(font, cnt, swatchX + 26, y + 6, 0xBBBBBB);
            g.fill(swatchX, y, swatchX + 20, y + 20, 0xFF000000 | Palette.shown(mc, i));
        }

        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font,
                editing
                        ? "painting into " + Palette.GROUP_NAMES[selected] + "  -  brush " + (EditBrush.size + 1)
                        : "hover a row to highlight it, drag the model to turn it",
                wheelX - 40, 30, editing ? 0x66FF66 : 0x888888);
    }

    private void drawBar(GuiGraphics g, int x, float value, int knob) {
        for (int i = 0; i < WHEEL; i++) {
            int v = 255 - i * 255 / WHEEL;
            g.fill(x, wheelY + i, x + 10, wheelY + i + 1, 0xFF000000 | (v << 16) | (v << 8) | v);
        }
        int y = wheelY + (int) ((1f - value) * (WHEEL - 2));
        g.fill(x - 3, y - 1, x + 13, y + 1, knob);
    }

    private float saturation() {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        return hsb[1];
    }

    private int countPixels(int group) {
        int[] effective = ScreenData.effectiveGroups(species, mc);
        if (effective == null) return 0;
        int n = 0;
        for (int v : effective) if (v == group) n++;
        return n;
    }

    private int groupUnderMouse(int mx, int my) {
        for (int i = 0; i < MonColors.GROUPS; i++) {
            int y = groupY + i * ROW_H;
            if (my >= y - 2 && my <= y + 22 && mx >= groupX - 4 && mx <= swatchX + 22) return i;
        }
        return -1;
    }

    private boolean overPreview(double mx, double my) {
        return mx >= previewX && mx <= previewX + previewW
                && my >= previewY && my <= previewY + previewH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        // drag the model
        if (overPreview(mx, my) && !isOverButton(mx, my)) {
            draggingPreview = true;
            return true;
        }

        int row = groupUnderMouse((int) mx, (int) my);
        if (row >= 0 && mx >= swatchX - 4) { selected = row; return true; }

        if (editing && !isOverButton(mx, my)) {
            int[] px = PaintPanel.pixelAt(species, mx, my, paintX, paintY, 280);
            if (px != null) {
                ScreenData.Size s = ScreenData.size(species);
                EditBrush.paint(mc, px[0], px[1], s.width(), s.height(), selected);
                ColorStore.touched(monId);
                return true;
            }
        }
        return pick(mx, my) || super.mouseClicked(mx, my, b);
    }

    private boolean isOverButton(double mx, double my) {
        for (var child : children()) {
            if (child instanceof Button btn && btn.isMouseOver(mx, my)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (draggingPreview) {
            yaw += (float) dx * 0.02f;
            pitch = Math.max(-1.2f, Math.min(1.2f, pitch + (float) dy * 0.02f));
            return true;
        }
        if (editing && !isOverButton(mx, my)) {
            int[] px = PaintPanel.pixelAt(species, mx, my, paintX, paintY, 280);
            if (px != null) {
                ScreenData.Size s = ScreenData.size(species);
                EditBrush.paint(mc, px[0], px[1], s.width(), s.height(), selected);
                ColorStore.touched(monId);
                return true;
            }
        }
        return pick(mx, my) || super.mouseDragged(mx, my, b, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        draggingPreview = false;
        ColorStore.save();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (overPreview(mx, my)) {
            EditBrush.size = Math.max(1, Math.min(4, EditBrush.size + (dy > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_LEFT) { yaw -= 0.12f; return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { yaw += 0.12f; return true; }
        if (key == GLFW.GLFW_KEY_UP) { pitch = Math.max(-1.2f, pitch - 0.08f); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { pitch = Math.min(1.2f, pitch + 0.08f); return true; }
        return super.keyPressed(key, scancode, mods);
    }

    private boolean pick(double mx, double my) {
        if (overBar(mx, my, barVX)) {
            brightness = clamp01(1f - (float) ((my - wheelY) / WHEEL));
            setFromWheel();
            return true;
        }
        if (overBar(mx, my, barSX)) {
            applySaturation(clamp01(1f - (float) ((my - wheelY) / WHEEL)));
            return true;
        }
        float r = WHEEL / 2f;
        double dx = mx - (wheelX + r), dy = my - (wheelY + r);
        double d = Math.sqrt(dx * dx + dy * dy) / r;
        if (d <= 1.0) {
            float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1) % 1);
            mc.colors[selected] = Color.HSBtoRGB(hue, (float) d, brightness) & 0xFFFFFF;
            ColorStore.touched(monId);
            return true;
        }
        return false;
    }

    private boolean overBar(double mx, double my, int x) {
        return mx >= x - 4 && mx <= x + 14 && my >= wheelY - 2 && my <= wheelY + WHEEL + 2;
    }

    private void setFromWheel() {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        mc.colors[selected] = Color.HSBtoRGB(hsb[0], hsb[1], brightness) & 0xFFFFFF;
        ColorStore.touched(monId);
    }

    private void applySaturation(float sat) {
        int c = Palette.shown(mc, selected);
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        mc.colors[selected] = Color.HSBtoRGB(hsb[0], sat, brightness) & 0xFFFFFF;
        ColorStore.touched(monId);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override
    public void onClose() {
        ColorStore.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
