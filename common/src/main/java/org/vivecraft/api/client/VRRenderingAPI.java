package org.vivecraft.api.client;

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
}
