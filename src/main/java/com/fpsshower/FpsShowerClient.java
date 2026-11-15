package com.fpsshower;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class FpsShowerClient implements ClientModInitializer {
    public static final String MOD_ID = "fps_shower";

    private static boolean wasDown = false;
    private static int lastKey = -2;

    @Override
    public void onInitializeClient() {
        Config.load();

        // "FPS: xx" in the top-left corner. Only plain text drawing -> works with VulkanMod.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of(MOD_ID, "fps_counter"),
                (context, tickCounter) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    context.drawText(mc.textRenderer, "FPS: " + mc.getCurrentFps(), 4, 4, 0xFFFFFFFF, true);
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(FpsShowerClient::onTick);
    }

    private static void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getWindow() == null) {
            QuickUse.reset();
            return;
        }

        long handle = mc.getWindow().getHandle();

        // Key is polled directly (no KeyBinding) so the mod never shows up in Controls.
        boolean down = Config.toggleKey != GLFW.GLFW_KEY_UNKNOWN
                && GLFW.glfwGetKey(handle, Config.toggleKey) == GLFW.GLFW_PRESS;

        if (Config.toggleKey != lastKey) {
            // key was just (re)bound: wait until it is released before reacting again
            lastKey = Config.toggleKey;
            wasDown = true;
        }

        if (down && !wasDown) {
            if (mc.currentScreen == null) {
                mc.setScreen(new FpsScreen());
            } else if (mc.currentScreen instanceof FpsScreen screen && !screen.isListening()) {
                mc.setScreen(null);
            }
        }
        wasDown = down;

        QuickUse.tick(mc);
    }
}
