package org.vivecraft.mod_compat_vr.iris.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Pseudo
@Mixin(targets = {
    "net.coderbot.iris.shadow.ShadowMatrices",
    "net.coderbot.iris.shadows.ShadowMatrices",
    "net.irisshaders.iris.shadows.ShadowMatrices"
})
public class IrisShadowMatricesMixin {

    @Unique
    private static Vec3 vivecraft$FIRST_PASS_POS;

    // offset camera pos, to be in the equal grid as the first pass, but with correct offset
    @ModifyVariable(method = "snapModelViewToGrid", at = @At(value = "STORE", ordinal = 0), ordinal = 1, remap = false)
    private static float vivecraft$modifyOffsetX(
        float xOffset, @Local(argsOnly = true) float shadowIntervalSize, @Share("curPos") LocalRef<Vec3> curPos)
    {
        if (!RenderPassType.isVanilla()) {
            curPos.set(RenderHelper.getSmoothCameraPosition(ClientDataHolderVR.getInstance().currentPass, ClientDataHolderVR.getInstance().vrPlayer.getVRDataWorld()));
            if (ClientDataHolderVR.getInstance().isFirstPass) {
                vivecraft$FIRST_PASS_POS = curPos.get();
            }
            return (float) (vivecraft$FIRST_PASS_POS.x % shadowIntervalSize - (vivecraft$FIRST_PASS_POS.x - curPos.get().x));
        } else {
            return xOffset;
        }
    }

    @ModifyVariable(method = "snapModelViewToGrid", at = @At(value = "STORE", ordinal = 0), ordinal = 2, remap = false)
    private static float vivecraft$modifyOffsetY(
        float yOffset, @Local(argsOnly = true) float shadowIntervalSize, @Share("curPos") LocalRef<Vec3> curPos)
    {
        if (!RenderPassType.isVanilla()) {
            return (float) (vivecraft$FIRST_PASS_POS.y % shadowIntervalSize - (vivecraft$FIRST_PASS_POS.y - curPos.get().y));
        } else {
            return yOffset;
        }
    }

    @ModifyVariable(method = "snapModelViewToGrid", at = @At(value = "STORE", ordinal = 0), ordinal = 3, remap = false)
    private static float vivecraft$modifyOffsetZ(
        float zOffset, @Local(argsOnly = true) float shadowIntervalSize, @Share("curPos") LocalRef<Vec3> curPos)
    {
        if (!RenderPassType.isVanilla()) {
            return (float) (vivecraft$FIRST_PASS_POS.z % shadowIntervalSize - (vivecraft$FIRST_PASS_POS.z - curPos.get().z));
        } else {
            return zOffset;
        }
    }
}
