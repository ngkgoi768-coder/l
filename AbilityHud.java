package com.trollmod.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The ability list on the right edge of the screen: name on the left, key in a box on the right.
 * An active ability is highlighted in red. To add an ability, add one line to ENTRIES.
 */
public final class AbilityHud {
    private record Entry(String nameKey, KeyMapping key, Predicate<UUID> active) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("ability.trollmod.invisibility", Keys.INVISIBILITY, ClientCaineState::isVanished)
    );

    private static final int RED = 0xFFE03030;
    private static final int ROW_HEIGHT = 20;
    private static final int BOX_HEIGHT = 16;

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        UUID self = mc.player.getUUID();
        if (!ClientCaineState.is(self)) {
            return;
        }

        Font font = mc.font;
        int y = height / 2 - ENTRIES.size() * ROW_HEIGHT / 2;
        for (Entry entry : ENTRIES) {
            boolean active = entry.active().test(self);
            Component name = Component.translatable(entry.nameKey());
            Component key = entry.key().getTranslatedKeyMessage();

            int boxWidth = Math.max(18, font.width(key) + 10);
            int boxRight = width - 6;
            int boxLeft = boxRight - boxWidth;
            int textY = y + (BOX_HEIGHT - 8) / 2;

            graphics.fill(boxLeft, y, boxRight, y + BOX_HEIGHT, active ? 0xE0B01818 : 0xB0101010);
            border(graphics, boxLeft, y, boxRight, y + BOX_HEIGHT, RED);
            graphics.drawString(font, key, boxLeft + (boxWidth - font.width(key)) / 2, textY,
                    active ? 0xFFFFFFFF : RED, false);
            graphics.drawString(font, name, boxLeft - 6 - font.width(name), textY,
                    active ? 0xFFFF5050 : 0xFFFFFFFF, true);
            y += ROW_HEIGHT;
        }
    };

    private static void border(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    private AbilityHud() {
    }
}
