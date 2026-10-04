package com.rpgcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** rpgcore key bindings: dash (left Alt, #19) and quest journal (J). */
public final class Keys {
    private Keys() {}

    public static final String CATEGORY = "key.categories.rpgcore";
    public static final KeyMapping DASH = new KeyMapping("key.rpgcore.dash", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyMapping JOURNAL = new KeyMapping("key.rpgcore.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
}
