package com.fontainerepublic.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Small shared drawing helpers for the FR client screens and HUD
 * (FR-CLIENT-001-IMPL-B). Rendering is display-only: every helper draws a
 * bounded card (translucent panel + border + title + lines) from
 * already-projected text and never touches any state or decision.
 */
public final class FrGuiUtil {

    /** Panel background (semi-transparent dark). */
    public static final int PANEL_BACKGROUND = 0xC0101010;

    /** Panel border. */
    public static final int PANEL_BORDER = 0xFF888888;

    /** Title color. */
    public static final int COLOR_TITLE = 0xFFFFFFFF;

    /** Body text color. */
    public static final int COLOR_BODY = 0xFFDDDDDD;

    /** Accent color (balance / incoming). */
    public static final int COLOR_ACCENT = 0xFF55FF55;

    /** Error color (bounded form feedback). */
    public static final int COLOR_ERROR = 0xFFFF5555;

    /** Muted color (placeholders / hints). */
    public static final int COLOR_MUTED = 0xFFAAAAAA;

    private FrGuiUtil() {
    }

    /**
     * Draws one card: translucent panel, border, a title line and up to
     * {@code maxLines} body lines (extra lines are dropped — a display bound;
     * callers paginate beforehand).
     */
    public static void drawCard(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            String title,
            List<String> lines,
            int maxLines
    ) {
        graphics.fill(x, y, x + width, y + height, PANEL_BACKGROUND);
        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + height, PANEL_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);
        graphics.drawString(font, title, x + 4, y + 4, COLOR_TITLE, false);
        int cursorY = y + 16;
        int drawn = 0;
        for (String line : lines) {
            if (drawn >= maxLines) {
                break;
            }
            graphics.drawString(font, line, x + 4, cursorY, COLOR_BODY, false);
            cursorY += 10;
            drawn++;
        }
    }
}
