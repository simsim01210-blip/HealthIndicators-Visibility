package safehealth.mixin;

import net.minecraft.class_1309;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import safehealth.HealthDraw;
import safehealth.IndicatorPlacement;
import safehealth.ModeControls;
import safehealth.MountedHealth;

@Mixin(targets = "net.minecraft.class_922", remap = false)
abstract class MountMixin {
    @Inject(method = "method_4054(Lnet/minecraft/class_1309;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
            at = @At("TAIL"))
    private void safehealth$afterMount(class_1309 mount, float yaw, float delta, class_4587 matrices,
                                     class_4597 consumers, int light, CallbackInfo ci) {
        if (!ModeControls.isStacked() && IndicatorPlacement.isMount(mount)
                && (Object)this instanceof HealthDraw draw)
            MountedHealth.drawDirect(mount, draw, delta, class_310.method_1551(), matrices, consumers, light);
    }
}
