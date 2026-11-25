package safehealth;

import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1498;
import net.minecraft.class_1657;
import net.minecraft.class_2960;
import net.minecraft.class_7689;

public final class IndicatorPlacement {
    public static final double LIFT = 3.0;
    public static final double LABEL_GAP = 0.5 + 0.25875;
    private static final class_2960 MOUNT_ICONS = new class_2960("healthindicators", "textures/gui/mount_icons.png");

    private IndicatorPlacement() {}

    public static boolean isMount(class_1297 entity) {
        return entity instanceof class_1498 || entity instanceof class_7689;
    }

    public static class_1657 firstRider(class_1297 mount) {
        for (var passenger : mount.method_5685()) {
            if (passenger instanceof class_1657 player) return player;
        }
        return null;
    }

    public static class_2960 heartTexture(class_2960 regular, class_1309 entity) {
        return isMount(entity) ? MOUNT_ICONS : regular;
    }

    public static int numberColor(int regular, class_1309 entity) {
        return isMount(entity) ? 0xFFB4F36B : regular;
    }
}
