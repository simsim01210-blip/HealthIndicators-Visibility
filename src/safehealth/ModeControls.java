package safehealth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.adytech99.healthindicators.HealthIndicatorsMod;
import io.github.adytech99.healthindicators.config.Config;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_304;
import net.minecraft.class_310;
import org.lwjgl.glfw.GLFW;

public final class ModeControls {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("healthindicators-mount-mode.txt");
    private static boolean stacked = true;

    private ModeControls() {}

    public static boolean isStacked() {
        return stacked;
    }

    public static void register() {
        try {
            if (Files.exists(FILE)) stacked = !Files.readString(FILE).strip().equals("2");
        } catch (IOException error) {
            HealthIndicatorsMod.LOGGER.warn("Could not load the mount display mode", error);
        }
        var switchMode = KeyBindingHelper.registerKeyBinding(new class_304("key.healthindicators.switch_mount_mode",
                GLFW.GLFW_KEY_UP, "key.categories.healthindicators"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (switchMode.method_1436()) {
                if (client.field_1724 == null || client.field_1755 != null) continue;
                stacked = !stacked;
                try {
                    Files.writeString(FILE, stacked ? "1" : "2");
                } catch (IOException error) {
                    HealthIndicatorsMod.LOGGER.warn("Could not save the mount display mode", error);
                }
                client.field_1705.method_1758(class_2561.method_43470(stacked ? "모드 1" : "모드 2"), false);
            }
        });
    }

    public static void showToggle(class_310 client) {
        if (client.field_1724 == null) return;
        boolean enabled = Config.getRenderingEnabled();
        var label = class_2561.method_43470("체력 바 ");
        label.method_10852(class_2561.method_43470(enabled ? "켜짐" : "꺼짐")
                .method_27692(enabled ? class_124.field_1060 : class_124.field_1061));
        client.field_1705.method_1758(label, false);
    }
}
