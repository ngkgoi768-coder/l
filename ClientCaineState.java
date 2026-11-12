package com.trollmod.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-side knowledge: which players are КЕЙН, and which of them are currently invisible. */
public final class ClientCaineState {
    private static final Set<UUID> CAINES = new HashSet<>();
    private static final Set<UUID> VANISHED = new HashSet<>();

    private ClientCaineState() {
    }

    public static void set(UUID player, boolean caine, boolean invisible) {
        if (caine) {
            CAINES.add(player);
        } else {
            CAINES.remove(player);
        }
        if (invisible) {
            VANISHED.add(player);
        } else {
            VANISHED.remove(player);
        }
    }

    public static boolean is(UUID player) {
        return CAINES.contains(player);
    }

    public static boolean isVanished(UUID player) {
        return VANISHED.contains(player);
    }

    public static void clear() {
        CAINES.clear();
        VANISHED.clear();
    }
}
