package com.rotproxy.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The shared black, crimson and violet rot-tools palette. */
public final class RotProxyTheme {
    public static final int BACKDROP = 0xF208080A;
    public static final int SURFACE = 0xFF18141C;
    public static final int SURFACE_ALT = 0xFF221A28;
    public static final int FIELD = 0xFF0C0A10;
    public static final int FIELD_ACTIVE = 0xFF24162E;
    public static final int BORDER = 0xFF4A3858;
    public static final int BORDER_BRIGHT = 0xFFE11D48;
    public static final int VIOLET = 0xFFA855F7;
    public static final int TEXT = 0xFFF8F5FF;
    public static final int TEXT_DIM = 0xFFE6DDF0;
    public static final int TEXT_MUTED = 0xFFC8BFD8;
    public static final int SUCCESS = 0xFF22C55E;
    public static final int WARNING = 0xFFF4C06A;
    public static final int ERROR = 0xFFFF4D6D;
    public static final int BUTTON = 0xFF2A1828;
    public static final int BUTTON_HOVER = 0xFF3D2048;
    public static final int BUTTON_DISABLED = 0xFF140E16;

    private RotProxyTheme() {}

    public static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE);
        drawOutline(graphics, x, y, width, height, BORDER);
        graphics.fill(x, y, x + width, y + 2, BORDER_BRIGHT);
    }

    public static void drawInset(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean focused) {
        graphics.fill(x, y, x + width, y + height, focused ? FIELD_ACTIVE : FIELD);
        drawOutline(graphics, x, y, width, height, focused ? VIOLET : BORDER);
    }

    public static void drawOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.outline(x, y, width, height, color);
    }
}
