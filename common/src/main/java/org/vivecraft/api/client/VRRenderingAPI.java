package org.vivecraft.api.client;

import com.google.common.annotations.Beta;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.api_impl.VRRenderingAPIImpl;
import org.vivecraft.client_vr.render.RenderPass;

/**
 * The main interface for interacting with Vivecraft from rendering code. For other client-side code, one should use
 * {@link VRClientAPI}.
 */
public interface VRRenderingAPI {

    static VRRenderingAPI getInstance() {
        return VRRenderingAPIImpl.INSTANCE;
    }

    /**
     * @return Whether the current render pass is a vanilla render pass.
     */
    boolean isVanillaRenderPass();

    /**
     * @return The current render pass Vivecraft is performing.
     */
    RenderPass getCurrentRenderPass();

    /**
     * @return Whether the current render pass is the first one it performed for this render cycle.
     */
    boolean isFirstRenderPass();

    /**
     * Gets the position that the provided {@link InteractionHand} renders at. Unlike
     * {@link org.vivecraft.api.data.VRPose#getHand(InteractionHand)} from {@link VRClientAPI#getWorldRenderPose()},
     * this returns a reasonable, default value for seated mode.
     * @param hand The hand to get the rendering position of.
     * @return The rendering position for the provided hand.
     */
    @Beta
    Vec3 getHandRenderPos(InteractionHand hand);

    /**
     * Sets the provided {@link PoseStack} to render at the position of the provided {@link InteractionHand}.
     * @param hand The hand to set the PoseStack to.
     * @param stack The PoseStack to be set.
     */
    @Beta
    void setupRenderingAtHand(InteractionHand hand, PoseStack stack);
}
