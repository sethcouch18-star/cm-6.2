package com.example.colormons;

/** Paints texture pixels into a chosen group. */
public class EditBrush {
    public static int size = 1;

    public static void paint(MonColors mc, int cx, int cy, int width, int height, int group) {
        int g = Math.max(0, Math.min(MonColors.GROUPS - 1, group));
        for (int dy = -size; dy <= size; dy++) for (int dx = -size; dx <= size; dx++) {
            if (dx * dx + dy * dy > size * size + size) continue;
            int x = cx + dx, y = cy + dy;
            if (x < 0 || y < 0 || x >= width || y >= height) continue;
            set(mc, y * width + x, g);
        }
    }

    public static void set(MonColors mc, int index, int group) {
        for (int i = 0; i < mc.editPixels.size(); i++) {
            if (mc.editPixels.get(i) == index) { mc.editGroups.set(i, group); return; }
        }
        mc.editPixels.add(index);
        mc.editGroups.add(group);
    }

    public static void undo(MonColors mc, int n) {
        for (int i = 0; i < n && !mc.editPixels.isEmpty(); i++) {
            mc.editPixels.remove(mc.editPixels.size() - 1);
            mc.editGroups.remove(mc.editGroups.size() - 1);
        }
    }
}
