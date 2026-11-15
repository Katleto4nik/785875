package com.fpsshower;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public final class Config {
    public enum Mode { OFF, CROSSBOW, PEARL, SHIELD }

    /**
     * Legit timing profiles (milliseconds). Every step gets its own random delay inside the range,
     * so the sequence is never the same twice. Actions still run on client ticks like normal input.
     */
    public enum Speed {
        FAST("Швидко", 45, 75, 55, 85, 40, 70),
        NORMAL("Нормально", 75, 125, 85, 135, 70, 120),
        SLOW("Повільно", 125, 190, 140, 210, 120, 190);

        public final String label;
        private final int useMin, useMax, retMin, retMax, relMin, relMax;

        Speed(String label, int useMin, int useMax, int retMin, int retMax, int relMin, int relMax) {
            this.label = label;
            this.useMin = useMin;
            this.useMax = useMax;
            this.retMin = retMin;
            this.retMax = retMax;
            this.relMin = relMin;
            this.relMax = relMax;
        }

        private static long rnd(int min, int max) {
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }

        /** pause between selecting the slot and using the item */
        public long useDelay() { return rnd(useMin, useMax); }

        /** pause between using the item and switching back */
        public long returnDelay() { return rnd(retMin, retMax); }

        /** pause between releasing RMB (shield) and switching back */
        public long releaseDelay() { return rnd(relMin, relMax); }

        public Speed next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static Mode mode = Mode.OFF;
    public static Speed speed = Speed.NORMAL;
    public static int toggleKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

    // which items in the main hand allow the RMB action
    public static boolean holdSword = true;
    public static boolean holdAnchor = false;
    public static boolean holdCrystal = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("fps_shower.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = GSON.fromJson(Files.readString(f), JsonObject.class);
            if (o == null) return;
            if (o.has("mode")) mode = Mode.valueOf(o.get("mode").getAsString());
            if (o.has("speed")) speed = Speed.valueOf(o.get("speed").getAsString());
            if (o.has("toggleKey")) toggleKey = o.get("toggleKey").getAsInt();
            if (o.has("holdSword")) holdSword = o.get("holdSword").getAsBoolean();
            if (o.has("holdAnchor")) holdAnchor = o.get("holdAnchor").getAsBoolean();
            if (o.has("holdCrystal")) holdCrystal = o.get("holdCrystal").getAsBoolean();
            if (!holdSword && !holdAnchor && !holdCrystal) holdSword = true;
        } catch (Exception ignored) {
            // broken config -> keep defaults
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("mode", mode.name());
            o.addProperty("speed", speed.name());
            o.addProperty("toggleKey", toggleKey);
            o.addProperty("holdSword", holdSword);
            o.addProperty("holdAnchor", holdAnchor);
            o.addProperty("holdCrystal", holdCrystal);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception ignored) {
            // not critical
        }
    }

    /** Readable key name without touching any Minecraft input classes. */
    public static String keyName(int code) {
        switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RIGHT SHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LEFT SHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RIGHT CTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LEFT CTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RIGHT ALT";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LEFT ALT";
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "CAPS LOCK";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE: return "BACKSPACE";
            case GLFW.GLFW_KEY_INSERT: return "INSERT";
            case GLFW.GLFW_KEY_DELETE: return "DELETE";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_PAGE_UP: return "PAGE UP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PAGE DOWN";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";
            case GLFW.GLFW_KEY_UNKNOWN: return "NONE";
            default: break;
        }
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) {
            return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        }
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) {
            return "NUM " + (code - GLFW.GLFW_KEY_KP_0);
        }
        String n = GLFW.glfwGetKeyName(code, 0);
        if (n != null && !n.isEmpty()) return n.toUpperCase();
        return "KEY " + code;
    }
}
