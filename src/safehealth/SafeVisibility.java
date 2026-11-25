package safehealth;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.adytech99.healthindicators.config.Config;
import io.github.adytech99.healthindicators.config.ModConfig;
import net.minecraft.class_1041;
import net.minecraft.class_1294;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_5636;
import net.minecraft.class_746;
import org.joml.Matrix4f;

/** Visibility conditions shared by hearts and numbers, evaluated during the actual name draw. */
public final class SafeVisibility {
    private static final double NAME_DISTANCE_SQUARED = 64 * 64;
    private static final double MOUNT_DISTANCE_SQUARED = 24 * 24;
    private static final float LABEL_SCALE = 0.025F;
    // The render thread owns this scratch matrix; no results persist between entities or frames.
    private static final Matrix4f clip = new Matrix4f();

    private SafeVisibility() {}

    public static boolean visible(class_1309 target, float tickDelta, class_310 client, class_4587 matrices) {
        return target instanceof class_1657 && ModConfig.HANDLER.instance().players
                && visible(target, tickDelta, client, matrices, null, 0, false);
    }

    static boolean visibleMount(class_1309 target, float tickDelta, class_310 client, class_4587 matrices,
                                class_243 anchor, double relativeHeight) {
        return visible(target, tickDelta, client, matrices, anchor, relativeHeight, false);
    }

    static boolean visibleDirectMount(class_1309 target, float tickDelta, class_310 client,
                                      class_4587 matrices, class_243 anchor, double relativeHeight) {
        return visible(target, tickDelta, client, matrices, anchor, relativeHeight, true);
    }

    private static boolean visible(class_1309 target, float tickDelta, class_310 client,
                                   class_4587 matrices, class_243 mountAnchor, double relativeHeight, boolean direct) {
        boolean mount = mountAnchor != null;
        if (!Config.getRenderingEnabled() || client == null || matrices == null || !target.method_5805()
                || !RenderSystem.isOnRenderThread()) return false;

        class_746 viewer = client.field_1724;
        if (viewer == null || viewer == target || client.field_1687 == null
                || target.method_5770() != client.field_1687 || !class_310.method_1498()
                || client.field_1755 != null || client.method_18506() != null
                || client.method_1560() != viewer || viewer.method_7325()
                || target.method_7325() || target.method_5767() || target.method_5756(viewer)
                || target.method_5715() || target.method_18276()
                || target.method_6059(class_1294.field_5905)
                || viewer.method_6059(class_1294.field_5919) || viewer.method_6059(class_1294.field_38092)) return false;

        class_4184 camera = client.field_1773.method_19418();
        if (!camera.method_19332() || camera.method_19331() != viewer) return false;
        class_5636 medium = camera.method_19334();
        if (medium == class_5636.field_27885 || medium == class_5636.field_27887) return false;

        double distanceSquared = client.method_1561().method_23168(target);
        float fogEnd = RenderSystem.getShaderFogEnd();
        if (!(distanceSquared >= 0 && distanceSquared < (mount ? MOUNT_DISTANCE_SQUARED : NAME_DISTANCE_SQUARED))
                || !(fogEnd > 0 && distanceSquared < (double) fogEnd * fogEnd)) return false;

        boolean inFrame = mount
                ? mountAnchorOnScreen(client, matrices, relativeHeight)
                    && (!direct || fullyOnScreen(target, client, matrices, distanceSquared, false))
                : fullyOnScreen(target, client, matrices, distanceSquared, true);
        return inFrame && Geometry.visible(target, tickDelta, client, mountAnchor);
    }

    private static boolean fullyOnScreen(class_1309 target, class_310 client, class_4587 matrices,
                                         double distanceSquared, boolean requireName) {
        class_1041 window = client.method_22683();
        int width = window.method_4489();
        int height = window.method_4506();
        if (width < 3 || height < 3) return false;
        // One framebuffer pixel of margin covers rasterization at the edge.
        float xLimit = 1F - 2F / width;
        float yLimit = 1F - 2F / height;
        RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), clip)
                .mul(matrices.method_23760().method_23761());

        // The player's name draw already includes the translation applied above a below-name score.
        // Undo it only for the body, whose bounds are relative to the unshifted entity origin.
        float scoreShift = requireName ? scoreboardLift((class_1657) target, distanceSquared) : 0;
        class_238 box = target.method_5829();
        float x0 = (float)(box.field_1323 - target.method_23317());
        float x1 = (float)(box.field_1320 - target.method_23317());
        float y0 = (float)(box.field_1322 - target.method_23318()) - scoreShift;
        float y1 = (float)(box.field_1325 - target.method_23318()) - scoreShift;
        float z0 = (float)(box.field_1321 - target.method_23321());
        float z1 = (float)(box.field_1324 - target.method_23321());
        for (int corner = 0; corner < 8; corner++) {
            if (!inside((corner & 1) == 0 ? x0 : x1, (corner & 2) == 0 ? y0 : y1,
                        (corner & 4) == 0 ? z0 : z1, xLimit, yLimit)) return false;
        }

        if (!requireName) return true;
        class_2561 name = target.method_5476();
        int textWidth = client.field_1772.method_27525(name);
        if (textWidth <= 0) return false;
        float left = -textWidth / 2F - 1;
        float right = textWidth / 2F + 1;
        float top = "deadmau5".equals(name.getString()) ? -11 : -1;
        float bottom = top + 11;
        clip.translate(0, target.method_51152(), 0).rotate(client.method_1561().method_24197())
                .scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        return inside(left, top, 0, xLimit, yLimit) && inside(right, top, 0, xLimit, yLimit)
                && inside(left, bottom, 0, xLimit, yLimit) && inside(right, bottom, 0, xLimit, yLimit);
    }

    static float scoreboardLift(class_1657 player, double distanceSquared) {
        return distanceSquared < 100 && player.method_7327().method_1189(2) != null
                ? 9F * 1.15F * LABEL_SCALE : 0;
    }

    private static boolean mountAnchorOnScreen(class_310 client, class_4587 matrices, double height) {
        class_1041 window = client.method_22683();
        int width = window.method_4489(), pixelHeight = window.method_4506();
        if (width < 3 || pixelHeight < 3 || !Double.isFinite(height)) return false;
        RenderSystem.getProjectionMatrix().mul(RenderSystem.getModelViewMatrix(), clip)
                .mul(matrices.method_23760().method_23761());
        return inside(0, (float)height, 0, 1F - 2F / width, 1F - 2F / pixelHeight);
    }

    private static boolean inside(float x, float y, float z, float xLimit, float yLimit) {
        float w = clip.m03() * x + clip.m13() * y + clip.m23() * z + clip.m33();
        if (!(w > 0) || !Float.isFinite(w)) return false;
        float cx = clip.m00() * x + clip.m10() * y + clip.m20() * z + clip.m30();
        float cy = clip.m01() * x + clip.m11() * y + clip.m21() * z + clip.m31();
        float cz = clip.m02() * x + clip.m12() * y + clip.m22() * z + clip.m32();
        return Math.abs(cx) <= w * xLimit && Math.abs(cy) <= w * yLimit && Math.abs(cz) < w;
    }
}
