package org.vivecraft.mod_compat_vr.emf.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import traben.entity_model_features.models.parts.EMFModelPart;

@Pseudo
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartWithState", remap = false)
public class EMFModelPartWithStateVRMixin {
    @Group(name = "no animation", min = 1, max = 1)
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Ltraben/entity_model_features/models/parts/EMFModelPartWithState;animationHolder:Ltraben/entity_model_features/models/parts/EMFModelPart$Animator;", ordinal = 0), require = 0, expect = 0)
    private EMFModelPart.Animator vivecraft$noAnimationForFirstPersonNew(EMFModelPart.Animator original) {
        return (!VRState.VR_RUNNING || !ClientDataHolderVR.IS_FP_HAND) ? original : null;
    }

    @Group(name = "no animation", min = 1, max = 1)
    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Ltraben/entity_model_features/models/parts/EMFModelPartWithState;tryAnimate:Ltraben/entity_model_features/models/parts/EMFModelPart$Animator;", ordinal = 0), require = 0, expect = 0)
    private EMFModelPart.Animator vivecraft$noAnimationForFirstPersonOld(EMFModelPart.Animator original) {
        return (!VRState.VR_RUNNING || !ClientDataHolderVR.IS_FP_HAND) ? original : null;
    }
}
