package org.vivecraft.client_xr.render_pass;

import com.mojang.blaze3d.pipeline.MainTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.render.RenderPass;

public class RenderPassManager {
    private static final Minecraft MC = Minecraft.getInstance();

    public static RenderPassManager INSTANCE;

    public final MainTarget vanillaRenderTarget;
    public PostChain vanillaOutlineChain;
    public PostChain vanillaPostEffect;
    public PostChain vanillaTransparencyChain;
    public static RenderPassType renderPassType = RenderPassType.VANILLA;
    public static WorldRenderPass WRP;

    public RenderPassManager(MainTarget vanillaRenderTarget) {
        this.vanillaRenderTarget = vanillaRenderTarget;
    }

    /**
     * sets the current pass to {@code wrp} <br>
     * this sets the main RenderTarget, and any post effect that is linked to that pass
     * @param wrp WorldRenderPass to set
     */
    public static void setWorldRenderPass(WorldRenderPass wrp) {
        RenderPassManager.WRP = wrp;
        renderPassType = RenderPassType.WORLD_ONLY;
        MC.mainRenderTarget = wrp.target;
        if (MC.gameRenderer != null) {
            MC.gameRenderer.postEffect = wrp.postEffect;
        }
    }

    /**
     * sets up rendering for the GUI, this binds the GUI RenderTarget
     */
    public static void setGUIRenderPass() {
        ClientDataHolderVR.getInstance().currentPass = RenderPass.GUI;
        RenderPassManager.WRP = null;
        renderPassType = RenderPassType.GUI_ONLY;
        MC.mainRenderTarget = GuiHandler.GUI_FRAMEBUFFER;
    }

    /**
     * resets back to the vanilla RenderPass
     */
    public static void setVanillaRenderPass() {
        ClientDataHolderVR.getInstance().currentPass = null;
        RenderPassManager.WRP = null;
        renderPassType = RenderPassType.VANILLA;
        MC.mainRenderTarget = INSTANCE.vanillaRenderTarget;
        if (MC.gameRenderer != null) {
            MC.gameRenderer.postEffect = INSTANCE.vanillaPostEffect;
        }
    }
}
