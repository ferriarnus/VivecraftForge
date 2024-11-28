package org.vivecraft.fabric.mixin.client.resources.model;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.trackers.CameraTracker;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;

@Mixin(ModelBakery.class)
public abstract class FabricModelBakeryMixin {
    @Shadow
    protected abstract void loadTopLevel(ModelResourceLocation modelResourceLocation);

    @Inject(method = "<init>", at = @At(value = "CONSTANT", args = "stringValue=special"))
    private void loadModels(CallbackInfo ci) {
        this.loadTopLevel(TelescopeTracker.SCOPE_MODEL);
        this.loadTopLevel(ClimbTracker.CLAWS_MODEL);
        this.loadTopLevel(ClientDataHolderVR.THIRD_PERSON_CAMERA_MODEL);
        this.loadTopLevel(ClientDataHolderVR.THIRD_PERSON_CAMERA_DISPLAY_MODEL);
        this.loadTopLevel(CameraTracker.CAMERA_MODEL);
        this.loadTopLevel(CameraTracker.CAMERA_DISPLAY_MODEL);
    }
}
