package safehealth;

import net.minecraft.class_1309;
import net.minecraft.class_4587;
import net.minecraft.class_4597;

public interface HealthDraw {
    void safehealth$draw(class_1309 target, float yaw, float tickDelta, class_4587 matrices,
                         class_4597 consumers, int light);

    void safehealth$drawHearts(class_1309 target, float yaw, float tickDelta, class_4587 matrices,
                               class_4597 consumers, int light);

    void safehealth$drawNumbers(class_1309 target, float yaw, float tickDelta, class_4587 matrices,
                                class_4597 consumers, int light);
}
