package org.vivecraft.mixin.client_vr.gui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.extensions.GuiExtension;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Mixin(Gui.class)
public abstract class GuiVRMixin implements GuiExtension {

    @Unique
    public boolean vivecraft$showPlayerList;

    @Shadow
    private int screenWidth;

    @Shadow
    private int screenHeight;

    @Final
    @Shadow
    private Minecraft minecraft;

    @Shadow
    @Final
    private static ResourceLocation HOTBAR_SELECTION_SPRITE;

    @Shadow
    protected abstract Player getCameraPlayer();

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelVignette(CallbackInfo ci) {
        if (RenderPassType.isGuiOnly()) {
            RenderSystem.enableDepthTest();
            ci.cancel();
        }
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelTextureOverlay(CallbackInfo ci) {
        if (RenderPassType.isGuiOnly()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelPortalOverlay(CallbackInfo ci) {
        if (RenderPassType.isGuiOnly()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelSpyglassOverlay(CallbackInfo ci) {
        if (RenderPassType.isGuiOnly()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void vivecraft$cancelCrosshair(CallbackInfo ci) {
        if (RenderPassType.isGuiOnly()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getSleepTimer()I", ordinal = 0))
    private int vivecraft$noSleepOverlay(int sleepTimer) {
        return VRState.VR_RUNNING ? 0 : sleepTimer;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean vivecraft$toggleableTabList(boolean keyDown) {
        return keyDown || this.vivecraft$showPlayerList;
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noHotbarOnScreens(CallbackInfo ci) {
        if (VRState.VR_RUNNING && this.minecraft.screen != null) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/HumanoidArm;getOpposite()Lnet/minecraft/world/entity/HumanoidArm;"))
    private HumanoidArm vivecraft$offhandSlotSide(HumanoidArm instance, Operation<HumanoidArm> original) {
        if (!VRState.VR_RUNNING) {
            return original.call(instance);
        } else {
            // show the offhand slot on the right when using reverse hands
            return ClientDataHolderVR.getInstance().vrSettings.reverseHands ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        }
    }

    @Inject(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 1, shift = At.Shift.AFTER))
    private void vivecraft$hotbarContextIndicator(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (VRState.VR_RUNNING && ClientDataHolderVR.getInstance().interactTracker.hotbar >= 0 &&
            ClientDataHolderVR.getInstance().interactTracker.hotbar < 9 &&
            this.getCameraPlayer().getInventory().selected != ClientDataHolderVR.getInstance().interactTracker.hotbar &&
            ClientDataHolderVR.getInstance().interactTracker.isActive(this.minecraft.player))
        {
            int middle = this.screenWidth / 2;
            RenderSystem.setShaderColor(0.0F, 1.0F, 0.0F, 1.0F);
            guiGraphics.blitSprite(HOTBAR_SELECTION_SPRITE,
                middle - 91 - 1 + ClientDataHolderVR.getInstance().interactTracker.hotbar * 20,
                this.screenHeight - 22 - 1, 24, 23);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }


    @ModifyExpressionValue(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0))
    private boolean vivecraft$offhandSlotAlwaysVisible(boolean offhandEmpty) {
        // the result is inverted, so we need to invert ours aswell
        return offhandEmpty && !(VRState.VR_RUNNING && ClientDataHolderVR.getInstance().vrSettings.vrTouchHotbar &&
            !ClientDataHolderVR.getInstance().vrSettings.seated
        );
    }

    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 2))
    private void vivecraft$renderVRHotbarLeftIndicator(
        GuiGraphics instance, ResourceLocation sprite, int x, int y, int width, int height, Operation<Void> original)
    {
        vivecraft$renderColoredIcon(instance, sprite, x, y, width, height, original);
    }

    @WrapOperation(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 3))
    private void vivecraft$renderVRHotbarRightIndicator(
        GuiGraphics instance, ResourceLocation sprite, int x, int y, int width, int height, Operation<Void> original)
    {
        vivecraft$renderColoredIcon(instance, sprite, x, y, width, height, original);
    }

    @Unique
    private void vivecraft$renderColoredIcon(
        GuiGraphics instance, ResourceLocation sprite, int x, int y, int width, int height, Operation<Void> original)
    {
        boolean changeColor = VRState.VR_RUNNING && ClientDataHolderVR.getInstance().interactTracker.hotbar == 9 &&
            ClientDataHolderVR.getInstance().interactTracker.isActive(this.minecraft.player);

        if (changeColor) {
            RenderSystem.setShaderColor(0.0F, 0.0F, 1.0F, 1.0F);
        }

        original.call(instance, sprite, x, y, width, height);

        if (changeColor) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Inject(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableBlend()V", remap = false))
    private void vivecraft$renderViveIcons(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (VRState.VR_RUNNING) {
            this.vivecraft$renderViveHudIcons(guiGraphics);
        }
    }

    /**
     * renders the vivecraft status icons above the hotbar
     * @param guiGraphics GuiGraphics to render with
     */
    @Unique
    private void vivecraft$renderViveHudIcons(GuiGraphics guiGraphics) {
        if (this.minecraft.getCameraEntity() instanceof Player player) {
            int icon = 0;
            MobEffect mobeffect = null;

            if (player.isSprinting()) {
                mobeffect = MobEffects.MOVEMENT_SPEED;
            }

            if (player.isVisuallySwimming()) {
                mobeffect = MobEffects.DOLPHINS_GRACE;
            }

            if (player.isShiftKeyDown()) {
                mobeffect = MobEffects.BLINDNESS;
            }

            if (player.isFallFlying()) {
                icon = -1;
            }
            if (ClientDataHolderVR.getInstance().crawlTracker.crawling) {
                icon = -2;
            }

            int x = this.minecraft.getWindow().getGuiScaledWidth() / 2 - 109;
            int y = this.minecraft.getWindow().getGuiScaledHeight() - 39;

            if (icon == -1) {
                guiGraphics.renderFakeItem(new ItemStack(Items.ELYTRA), x, y);
                mobeffect = null;
            } else if (icon == -2) {
                int x2 = x;
                if (player.isShiftKeyDown()) {
                    x2 -= 19;
                } else {
                    mobeffect = null;
                }
                guiGraphics.renderFakeItem(new ItemStack(Items.RABBIT_FOOT), x2, y);
            }
            if (mobeffect != null) {
                TextureAtlasSprite textureatlassprite = this.minecraft.getMobEffectTextures().get(mobeffect);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                guiGraphics.blit(x, y, 0, 18, 18, textureatlassprite);
            }
        }
    }

    @Override
    @Unique
    public boolean vivecraft$getShowPlayerList() {
        return this.vivecraft$showPlayerList;
    }

    @Override
    @Unique
    public void vivecraft$setShowPlayerList(boolean showPlayerList) {
        this.vivecraft$showPlayerList = showPlayerList;
    }
}
