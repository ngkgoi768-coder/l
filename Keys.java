package com.trollmod.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Ability keys. They show up in Options -> Controls, so the player can rebind them. */
public final class Keys {
    public static final String CATEGORY = "key.categories.trollmod";

    public static final KeyMapping INVISIBILITY =
            new KeyMapping("key.trollmod.invisibility", GLFW.GLFW_KEY_X, CATEGORY);

    private Keys() {
    }
}
