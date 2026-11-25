package safehealth.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.adytech99.healthindicators.config.Config;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import safehealth.HealthDraw;
import safehealth.MountedHealth;


@Mixin(targets = "net.minecraft.class_897", remap = false)
abstract class LabelMixin {
    private static final String LABEL = "method_3926(Lnet/minecraft/class_1297;Lnet/minecraft/class_2561;Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V";

    // Record the opaque foreground glyph pass, after the wrapped call returns.
    // A cancelled label or a skipped draw leaves the per-invocation reference empty.
    @WrapOperation(method = LABEL, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/class_327;method_30882(Lnet/minecraft/class_2561;FFIZLorg/joml/Matrix4f;Lnet/minecraft/class_4597;Lnet/minecraft/class_327$class_6415;II)I"))
    private int safehealth$recordName(class_327 renderer, class_2561 text, float x, float y,
                                      int color, boolean shadow, Matrix4f position, class_4597 consumers,
                                      class_327.class_6415 layer, int background, int light,
                                      Operation<Integer> next, @Share("safehealthDrawnName") LocalRef<class_2561> drawn) {
        int end = next.call(renderer, text, x, y, color, shadow, position, consumers, layer, background, light);
        if ((Object) this instanceof HealthDraw && end > 0 && (color >>> 24) == 255
                && layer == class_327.class_6415.field_33993 && !text.getString().isBlank()) {
            drawn.set(text);
        }
        return end;
    }

    // This runs on the original matrix after vanilla restores it. Each nested label
    // gets its own LocalRef, so cancellation cannot arm a later player's health bar.
    @Inject(method = LABEL, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/class_4587;method_22909()V"))
    private void safehealth$afterName(class_1297 entity, class_2561 requested, class_4587 matrices,
                                      class_4597 consumers, int light, CallbackInfo ci,
                                      @Share("safehealthDrawnName") LocalRef<class_2561> drawn) {
        if (!(entity instanceof class_1657 player) || !((Object) this instanceof HealthDraw health)
                || !Config.getRenderingEnabled()) return;
        class_2561 name = drawn.get();
        if (name == null || !name.equals(requested) || !name.equals(player.method_5476())) return;
        class_310 client = class_310.method_1551();
        if (client == null || client.field_1724 == null) return;
        float tickDelta = client.method_1488();
        health.safehealth$draw(player, 0, tickDelta, matrices, consumers, light);
        MountedHealth.drawAbove(player, tickDelta, client, matrices, consumers, light);
    }
}
