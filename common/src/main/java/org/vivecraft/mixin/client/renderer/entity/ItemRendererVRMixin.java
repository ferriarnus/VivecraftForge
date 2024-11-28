package org.vivecraft.mixin.client.renderer.entity;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.trackers.ClimbTracker;
import org.vivecraft.client_vr.gameplay.trackers.TelescopeTracker;

@Mixin(ItemRenderer.class)
public class ItemRendererVRMixin {

    @Shadow
    @Final
    private ItemModelShaper itemModelShaper;

    @ModifyVariable(method = "getModel", at = @At(value = "STORE"))
    private BakedModel vivecraft$modelOverride(BakedModel bakedModel, ItemStack itemStack) {
        if (VRState.VR_RUNNING && itemStack.is(Items.SPYGLASS)) {
            return this.itemModelShaper.getModelManager().getModel(TelescopeTracker.SCOPE_MODEL);
        }
        if (ClimbTracker.isClaws(itemStack)) {
            return this.itemModelShaper.getModelManager().getModel(ClimbTracker.CLAWS_MODEL);
        }
        return bakedModel;
    }

// hand item fade
// needs custom item renderer, since the regular one doesn't accept a non 1.0 alpha
/*
    @Unique
    float vivecraft$fade = 1.0F;
    @Unique
    float vivecraft$manualFade = 1.0F;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", shift = At.Shift.AFTER))
    private void vivecraft$fade(ItemStack itemStack, ItemDisplayContext itemDisplayContext, boolean bl, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, BakedModel bakedModel, CallbackInfo ci) {
        LocalPlayer localplayer = Minecraft.getInstance().player;
        this.vivecraft$fade = localplayer != null && ClientDataHolderVR.isfphand
                              ? SwingTracker.getItemFade(localplayer, itemStack)
                              : this.vivecraft$manualFade;
    }

    @ModifyVariable(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderType(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/client/renderer/RenderType;"), ordinal = 0)
    private RenderType vivecraft$rendertypeFade(RenderType rendertype) {
        if (ClientDataHolderVR.isfphand && this.vivecraft$fade < 1.0F) {
            return Sheets.translucentCullBlockSheet();
        }
        return rendertype;
    }
*/
}
