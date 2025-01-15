package org.vivecraft.client.api_impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.client.VRRenderingAPI;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.render.helpers.RenderHelper;
import org.vivecraft.client_xr.render_pass.RenderPassType;

public class VRRenderingAPIImpl implements VRRenderingAPI {

    public static final VRRenderingAPIImpl INSTANCE = new VRRenderingAPIImpl();

    private VRRenderingAPIImpl() {
    }

    @Override
    public boolean isVanillaRenderPass() {
        return RenderPassType.isVanilla();
    }

    @Override
    public RenderPass getCurrentRenderPass() {
        return ClientDataHolderVR.getInstance().currentPass;
    }

    @Override
    public boolean isFirstRenderPass() {
        return ClientDataHolderVR.getInstance().isFirstPass;
    }

    @Override
    public Vec3 getHandRenderPos(InteractionHand hand) {
        return RenderHelper.getControllerRenderPos(hand.ordinal());
    }

    @Override
    public void setupRenderingAtHand(InteractionHand hand, PoseStack stack) {
        RenderHelper.setupRenderingAtController(hand.ordinal(), stack);
    }
}
