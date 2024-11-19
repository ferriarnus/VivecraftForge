package org.vivecraft.mod_compat_vr.pehkui;

import net.minecraft.world.entity.Entity;
import org.vivecraft.client.Xplat;
import virtuoel.pehkui.util.ScaleUtils;

public class PehkuiHelper {

    public static boolean isLoaded() {
        return Xplat.isModLoaded("pehkui");
    }

    /**
     * gets the current eye height scale of the give Entity
     * @param entity Entity to get the eye height scale for
     * @param partialTick current partial tick
     * @return scale of the entities eye height
     */
    public static float getEntityEyeHeightScale(Entity entity, float partialTick) {
        return ScaleUtils.getEyeHeightScale(entity, partialTick);
    }

    /**
     * gets the current bounding box scale of the give Entity
     * @param entity Entity to get the bounding box scale for
     * @param partialTick current partial tick
     * @return scale of the entities bounding box
     */
    public static float getEntityBbScale(Entity entity, float partialTick) {
        return ScaleUtils.getBoundingBoxHeightScale(entity, partialTick);
    }
}
