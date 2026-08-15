package com.fontainerepublic.trade.securetrade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;

/**
 * Verbatim port of Navielon/SecureTrade's {@code XPMath} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}).
 */
public class XPMath {

    public static long getPlayerXP(net.minecraft.world.entity.player.Player player) {
        int level = player.experienceLevel;
        long xp = getXpForLevels(level);
        xp += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        return xp;
    }

    public static long getXpForLevels(int level) {
        if (level <= 16) {
            return (long) level * level + 6L * level;
        } else if (level <= 31) {
            return Math.round(2.5D * level * level - 40.5D * level + 360D);
        } else {
            return Math.round(4.5D * level * level - 162.5D * level + 2220D);
        }
    }

    public static int getLevelForXp(long xp) {
        if (xp <= 0) {
            return 0;
        }

        int low = 0;
        int high = 1;
        while (high < Integer.MAX_VALUE / 2 && getXpForLevels(high) <= xp) {
            high *= 2;
        }

        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            if (getXpForLevels(mid) <= xp) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static void setPlayerXP(ServerPlayer player, long totalXp) {
        long clampedXp = Math.max(0L, totalXp);
        player.experienceLevel = getLevelForXp(clampedXp);
        long xpForCurrentLevel = getXpForLevels(player.experienceLevel);
        long xpForNextLevel = getXpForLevels(player.experienceLevel + 1);
        long difference = xpForNextLevel - xpForCurrentLevel;
        if (difference > 0) {
            player.experienceProgress = (float) (clampedXp - xpForCurrentLevel) / (float) difference;
        } else {
            player.experienceProgress = 0.0f;
        }
        player.experienceProgress = Math.max(0.0f, Math.min(1.0f, player.experienceProgress));
        player.totalExperience = (int) Math.min(Integer.MAX_VALUE, clampedXp);
        player.connection.send(new ClientboundSetExperiencePacket(
            player.experienceProgress,
            player.totalExperience,
            player.experienceLevel
        ));
    }
}
