package org.vivecraft.mod_compat_vr.origins;

import org.vivecraft.client.Xplat;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.ClimbingPower;
import io.github.apace100.apoli.power.TogglePower;
import net.minecraft.world.entity.LivingEntity;

public class OriginsHelper {
    @SuppressWarnings("ConstantConditions")
    public static boolean isLoaded() {
        return Xplat.isModLoaded("origins") && Xplat.getModloader().isFabric();
    }

    public static boolean hasClimbingPower(LivingEntity entity) {
        // assumes toggle is for climbing, which is correct at least for stock origins
        return !PowerHolderComponent.KEY.get(entity).getPowers(ClimbingPower.class, true).isEmpty()
            && !PowerHolderComponent.KEY.get(entity).getPowers(TogglePower.class, false).isEmpty();
    }
}
