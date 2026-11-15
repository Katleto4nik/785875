package com.fpsshower;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class FpsScreen extends Screen {
    private ButtonWidget crossbowBtn;
    private ButtonWidget pearlBtn;
    private ButtonWidget shieldBtn;
    private ButtonWidget swordBtn;
    private ButtonWidget anchorBtn;
    private ButtonWidget crystalBtn;
    private ButtonWidget speedBtn;
    private ButtonWidget bindBtn;

    private boolean listening = false;
    private boolean armed = false;

    public FpsScreen() {
        super(Text.literal("FPS shower"));
    }

    public boolean isListening() {
        return listening;
    }

    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        int x = this.width / 2 - w / 2;
        int top = this.height / 2 - 95;

        // --- modes ---
        crossbowBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> select(Config.Mode.CROSSBOW))
                .dimensions(x, top, w, h).build());
        pearlBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> select(Config.Mode.PEARL))
                .dimensions(x, top + 24, w, h).build());
        shieldBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> select(Config.Mode.SHIELD))
                .dimensions(x, top + 48, w, h).build());

        // --- items that must be in the main hand ---
        int hy = top + 90;
        swordBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> toggleHold(0))
                .dimensions(x, hy, 64, h).build());
        anchorBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> toggleHold(1))
                .dimensions(x + 68, hy, 64, h).build());
        crystalBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> toggleHold(2))
                .dimensions(x + 136, hy, 64, h).build());

        // --- legit speed profile ---
        speedBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cycleSpeed())
                .dimensions(x, top + 118, w, h).build());

        // --- rebind HUD key ---
        bindBtn = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> startListening())
                .dimensions(x, top + 142, w, h).build());

        refresh();
    }

    private void select(Config.Mode mode) {
        // clicking the active mode again turns the mod off
        Config.mode = (Config.mode == mode) ? Config.Mode.OFF : mode;
        Config.save();
        refresh();
    }

    private void toggleHold(int which) {
        boolean s = Config.holdSword;
        boolean a = Config.holdAnchor;
        boolean c = Config.holdCrystal;
        switch (which) {
            case 0 -> s = !s;
            case 1 -> a = !a;
            default -> c = !c;
        }
        // at least one item must stay enabled
        if (!s && !a && !c) return;
        Config.holdSword = s;
        Config.holdAnchor = a;
        Config.holdCrystal = c;
        Config.save();
        refresh();
    }

    private void cycleSpeed() {
        Config.speed = Config.speed.next();
        Config.save();
        refresh();
    }

    private void startListening() {
        if (listening) return;
        listening = true;
        armed = false;
        refresh();
    }

    private Text modeLabel(String name, Config.Mode mode) {
        if (Config.mode == mode) {
            return Text.literal(name + " [ВКЛ]").formatted(Formatting.GREEN);
        }
        return Text.literal(name);
    }

    private Text holdLabel(String name, boolean on) {
        return Text.literal(name).formatted(on ? Formatting.GREEN : Formatting.GRAY);
    }

    private void refresh() {
        if (crossbowBtn == null) return;
        crossbowBtn.setMessage(modeLabel("Арбалет", Config.Mode.CROSSBOW));
        pearlBtn.setMessage(modeLabel("Ендер-перл", Config.Mode.PEARL));
        shieldBtn.setMessage(modeLabel("Щит", Config.Mode.SHIELD));
        swordBtn.setMessage(holdLabel("Меч", Config.holdSword));
        anchorBtn.setMessage(holdLabel("Якір", Config.holdAnchor));
        crystalBtn.setMessage(holdLabel("Кристал", Config.holdCrystal));
        speedBtn.setMessage(Text.literal("Швидкість: " + Config.speed.label));
        if (listening) {
            bindBtn.setMessage(Text.literal("Натисни будь-яку кнопку...").formatted(Formatting.YELLOW));
        } else {
            bindBtn.setMessage(Text.literal("Кнопка худа: " + Config.keyName(Config.toggleKey)));
        }
    }

    @Override
    public void tick() {
        if (!listening) return;

        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        int pressed = -1;
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            if (GLFW.glfwGetKey(handle, k) == GLFW.GLFW_PRESS) {
                pressed = k;
                break;
            }
        }

        // ignore keys that were already held when listening started
        if (!armed) {
            if (pressed == -1) armed = true;
            return;
        }

        if (pressed != -1) {
            listening = false;
            if (pressed != GLFW.GLFW_KEY_ESCAPE) {
                Config.toggleKey = pressed;
                Config.save();
            }
            refresh();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Esc cancels rebinding instead of closing the screen
        return !listening;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int top = this.height / 2 - 95;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, top - 28, 0xFFFFFFFF);

        String current = switch (Config.mode) {
            case CROSSBOW -> "Арбалет";
            case PEARL -> "Ендер-перл";
            case SHIELD -> "Щит";
            default -> "вимкнено";
        };
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Режим: " + current), cx, top - 14, 0xFFAAAAAA);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Предмет в руці для ПКМ:"), cx, top + 76, 0xFFFFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Зелений = з цим предметом в руці працює ПКМ"), cx, top + 172, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Натисни на активний режим ще раз, щоб вимкнути"), cx, top + 184, 0xFFAAAAAA);
    }
}
