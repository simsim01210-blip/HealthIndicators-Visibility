package safehealth.mixin;

import net.minecraft.class_1297;
import net.minecraft.class_5577;
import net.minecraft.class_638;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = class_638.class, remap = false)
public interface ClientWorldLookup {
    @Invoker(value = "method_31592", remap = false)
    class_5577<class_1297> healthIndicators$getEntityLookup();
}
