package org.vivecraft.client.utils;

import net.minecraft.world.entity.LivingEntity;
import org.vivecraft.mod_compat_vr.pehkui.PehkuiHelper;

public class ScaleHelper {
    /**
     * gets the current eye height scale of the give Entity
     * @param entity Entity to get the eye height scale for
     * @param partialTick current partial tick
     * @return scale of the entities eye height
     */
    public static float getEntityEyeHeightScale(LivingEntity entity, float partialTick) {
        float scale = 1F;
        if (PehkuiHelper.isLoaded()) {
            scale *= PehkuiHelper.getEntityEyeHeightScale(entity, partialTick);
        }
        return scale;
    }

    /**
     * gets the current bounding box scale of the give Entity
     * @param entity Entity to get the bounding box scale for
     * @param partialTick current partial tick
     * @return scale of the entities bounding box
     */
    public static float getEntityBbScale(LivingEntity entity, float partialTick) {
        float scale = 1F;
        if (PehkuiHelper.isLoaded()) {
            scale *= PehkuiHelper.getEntityBbScale(entity, partialTick);
        }
        return scale;
    }
}
