package safehealth;

import io.github.adytech99.healthindicators.config.ModConfig;
import io.github.adytech99.healthindicators.enums.HealthDisplayTypeEnum;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4597;

public final class MountedHealth {
    private MountedHealth() {}

    public static void drawAbove(class_1657 rider, float delta, class_310 client, class_4587 matrices,
                                 class_4597 consumers, int light) {
        if (!ModeControls.isStacked() || !(rider.method_5854() instanceof class_1309 mount) || !IndicatorPlacement.isMount(mount)
                || IndicatorPlacement.firstRider(mount) != rider
                || !SafeVisibility.visible(rider, delta, client, matrices)
                || !(client.method_1561().method_3953(mount) instanceof HealthDraw draw)) return;

        var config = ModConfig.HANDLER.instance();
        boolean hearts = config.indicator_type == HealthDisplayTypeEnum.HEARTS;
        int perRow = Math.max(1, config.hearts_per_row);
        double playerRows = Math.ceil(Math.ceil((rider.method_6063() + rider.method_6067()) / 2.0) / perRow);
        double stackGap = 0.34 + (hearts ? Math.max(0, playerRows - 1) * 0.25 : 0);
        double top = rider.method_17682() + IndicatorPlacement.LABEL_GAP + stackGap;
        double scoreboardLift = SafeVisibility.scoreboardLift(rider, client.method_1561().method_23168(rider));
        double interpolation = 1.0 - delta;
        var anchor = new class_243(
                rider.method_23317() + (rider.field_6038 - rider.method_23317()) * interpolation,
                rider.method_23318() + (rider.field_5971 - rider.method_23318()) * interpolation
                        + top + IndicatorPlacement.LIFT * 0.025 + scoreboardLift,
                rider.method_23321() + (rider.field_5989 - rider.method_23321()) * interpolation);
        if (!SafeVisibility.visibleMount(mount, delta, client, matrices, anchor, top + IndicatorPlacement.LIFT * 0.025)) return;

        matrices.method_22903();
        try {
            // The raw renderer adds the mount's height and the usual half-block gap.
            matrices.method_22904(0, top - mount.method_17682() - 0.5, 0);
            drawIndicator(draw, mount, delta, matrices, consumers, light);
        } finally {
            matrices.method_22909();
        }
    }

    public static void drawDirect(class_1309 mount, HealthDraw draw, float delta, class_310 client,
                                  class_4587 matrices, class_4597 consumers, int light) {
        var origin = mount.method_30950(delta);
        double lift = mount instanceof net.minecraft.class_7689 ? 0.0 : 0.14;
        double height = mount.method_17682() + 0.5 + lift + IndicatorPlacement.LIFT * 0.025;
        var anchor = origin.method_1031(0, height, 0);
        if (!SafeVisibility.visibleDirectMount(mount, delta, client, matrices, anchor, height)) return;
        matrices.method_22903();
        try {
            matrices.method_22904(0, lift, 0);
            drawIndicator(draw, mount, delta, matrices, consumers, light);
        } finally {
            matrices.method_22909();
        }
    }

    private static void drawIndicator(HealthDraw draw, class_1309 mount, float delta, class_4587 matrices,
                                      class_4597 consumers, int light) {
        var type = ModConfig.HANDLER.instance().indicator_type;
        if (type == HealthDisplayTypeEnum.HEARTS) draw.safehealth$drawHearts(mount, 0, delta, matrices, consumers, light);
        else if (type == HealthDisplayTypeEnum.NUMBER) draw.safehealth$drawNumbers(mount, 0, delta, matrices, consumers, light);
    }
}
