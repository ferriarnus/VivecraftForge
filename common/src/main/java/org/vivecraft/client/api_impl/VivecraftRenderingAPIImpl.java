package org.vivecraft.client.api_impl;

import org.vivecraft.api.client.VivecraftRenderingAPI;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_xr.render_pass.RenderPassType;

public class VivecraftRenderingAPIImpl implements VivecraftRenderingAPI {

    public static final VivecraftRenderingAPIImpl INSTANCE = new VivecraftRenderingAPIImpl();

    private VivecraftRenderingAPIImpl() {
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
}
