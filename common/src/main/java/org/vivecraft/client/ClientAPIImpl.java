package org.vivecraft.client;

import org.vivecraft.api_beta.client.VivecraftClientAPI;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_xr.render_pass.RenderPassType;

public final class ClientAPIImpl implements VivecraftClientAPI {

    public static final ClientAPIImpl INSTANCE = new ClientAPIImpl();

    private ClientAPIImpl() {}

    @Override
    public boolean isVrInitialized() {
        return VRState.VR_INITIALIZED;
    }

    @Override
    public boolean isVrActive() {
        return VRState.VR_RUNNING;
    }

    @Override
    public boolean isVanillaRenderPass() {
        return RenderPassType.isVanilla();
    }
}
