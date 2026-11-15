package com.fpsshower;

import com.fpsshower.Config.Mode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Legit quick-use.
 *
 * RMB with an allowed item (sword / respawn anchor / end crystal, chosen in the HUD) in the main hand:
 *  - CROSSBOW: select a loaded crossbow from the hotbar, shoot, switch back
 *  - PEARL:    select an ender pearl from the hotbar, throw, switch back
 *  - SHIELD:   select the shield from the hotbar and block while RMB is held, switch back after release
 *
 * What makes it "legit":
 *  - only hotbar slot selection + the normal item-use call, exactly like a player scrolling and clicking
 *  - never selects and uses in the same tick: every step has its own random delay (see Config.Speed)
 *  - no rotation changes, no extra packets, no rendering hooks (VulkanMod safe)
 *  - skips items that are on cooldown and does nothing while another item is being used
 *  - if the player scrolls / presses a hotbar key / opens a screen mid-sequence, the mod backs off
 */
public final class QuickUse {
    private enum Stage { IDLE, WAIT_USE, WAIT_RETURN, SHIELD_HOLD, SHIELD_RELEASE }

    private static Stage stage = Stage.IDLE;
    private static int returnSlot = -1;
    private static int actionSlot = -1;
    private static Mode activeMode = Mode.OFF;
    private static boolean wasUse = false;
    private static long dueAt = 0L;

    private QuickUse() {}

    public static void reset() {
        stage = Stage.IDLE;
        wasUse = false;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || !player.isAlive()) {
            reset();
            return;
        }

        boolean useDown = mc.currentScreen == null && mc.options.useKey.isPressed();
        boolean pressed = useDown && !wasUse;
        wasUse = useDown;

        PlayerInventory inv = player.getInventory();
        long now = System.currentTimeMillis();

        // a screen opened in the middle of a sequence: put the slot back and stop
        if (stage != Stage.IDLE && mc.currentScreen != null) {
            restore(inv);
            return;
        }

        switch (stage) {
            case IDLE -> {
                if (pressed && Config.mode != Mode.OFF) {
                    start(mc, player, inv, now);
                }
            }
            case WAIT_USE -> {
                // the player took over the hotbar -> back off without touching anything
                if (inv.getSelectedSlot() != actionSlot) {
                    stage = Stage.IDLE;
                    return;
                }
                // shield: RMB was released before we even used it
                if (activeMode == Mode.SHIELD && !useDown) {
                    stage = Stage.SHIELD_RELEASE;
                    dueAt = now + Config.speed.releaseDelay();
                    return;
                }
                if (now < dueAt) return;

                useItem(mc, player);
                if (activeMode == Mode.SHIELD) {
                    stage = Stage.SHIELD_HOLD;
                } else {
                    stage = Stage.WAIT_RETURN;
                    dueAt = now + Config.speed.returnDelay();
                }
            }
            case WAIT_RETURN -> {
                if (inv.getSelectedSlot() != actionSlot) {
                    stage = Stage.IDLE;
                    return;
                }
                if (now >= dueAt) restore(inv);
            }
            case SHIELD_HOLD -> {
                if (inv.getSelectedSlot() != actionSlot) {
                    stage = Stage.IDLE;
                    return;
                }
                if (!useDown) {
                    stage = Stage.SHIELD_RELEASE;
                    dueAt = now + Config.speed.releaseDelay();
                }
            }
            case SHIELD_RELEASE -> {
                if (now >= dueAt) restore(inv);
            }
        }
    }

    private static void useItem(MinecraftClient mc, ClientPlayerEntity player) {
        if (activeMode == Mode.SHIELD) {
            // RMB is still physically held, vanilla keeps the shield raised
            if (!player.isUsingItem()) {
                mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            }
            return;
        }
        ActionResult result = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (activeMode == Mode.PEARL && result.isAccepted()) {
            player.swingHand(Hand.MAIN_HAND); // vanilla swings the arm when a pearl is thrown
        }
    }

    private static void restore(PlayerInventory inv) {
        // if the player switched slots manually in the meantime, don't override that
        if (inv.getSelectedSlot() == actionSlot && returnSlot >= 0) {
            inv.setSelectedSlot(returnSlot);
        }
        stage = Stage.IDLE;
    }

    private static boolean isTriggerItem(ItemStack s) {
        return (Config.holdSword && s.isIn(ItemTags.SWORDS))
                || (Config.holdAnchor && s.isOf(Items.RESPAWN_ANCHOR))
                || (Config.holdCrystal && s.isOf(Items.END_CRYSTAL));
    }

    private static void start(MinecraftClient mc, ClientPlayerEntity player, PlayerInventory inv, long now) {
        if (player.isUsingItem()) return;
        if (!isTriggerItem(player.getMainHandStack())) return;

        Mode mode = Config.mode;
        int slot = -1;
        boolean foundUnloaded = false;

        for (int i = 0; i < 9 && slot < 0; i++) {
            ItemStack s = inv.getStack(i);
            if (mode == Mode.PEARL && s.isOf(Items.ENDER_PEARL)) {
                slot = i;
            } else if (mode == Mode.SHIELD && s.isOf(Items.SHIELD)) {
                slot = i;
            } else if (mode == Mode.CROSSBOW && s.isOf(Items.CROSSBOW)) {
                if (CrossbowItem.isCharged(s)) {
                    slot = i;
                } else {
                    foundUnloaded = true;
                }
            }
        }

        if (slot < 0) {
            String msg = switch (mode) {
                case PEARL -> "Ендер-перлів немає в хотбарі";
                case SHIELD -> "Щита немає в хотбарі";
                case CROSSBOW -> foundUnloaded ? "Арбалет не заряджений" : "Арбалета немає в хотбарі";
                default -> "";
            };
            if (!msg.isEmpty()) {
                player.sendMessage(Text.literal("FPS shower: " + msg), true);
            }
            return;
        }

        // an item on cooldown can't be used anyway, a real player wouldn't swap to it
        if ((mode == Mode.PEARL || mode == Mode.SHIELD)
                && player.getItemCooldownManager().isCoolingDown(inv.getStack(slot))) {
            String msg = (mode == Mode.PEARL) ? "Ендер-перл на кулдауні" : "Щит на кулдауні";
            player.sendMessage(Text.literal("FPS shower: " + msg), true);
            return;
        }

        returnSlot = inv.getSelectedSlot();
        actionSlot = slot;
        activeMode = mode;
        inv.setSelectedSlot(slot);

        // pearl / crossbow are single actions: this press is "spent", so vanilla's
        // hold-to-repeat can't fire a second use while we wait for the delays
        if (mode != Mode.SHIELD) {
            mc.options.useKey.setPressed(false);
        }

        stage = Stage.WAIT_USE;
        dueAt = now + Config.speed.useDelay();
    }
}
