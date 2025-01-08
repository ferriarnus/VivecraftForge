package org.vivecraft.client_vr.provider.control;

import net.minecraft.client.KeyMapping;
import org.vivecraft.client_vr.ClientDataHolderVR;

public class VivecraftMovementInput {
    public static float getMovementAxisValue(KeyMapping keyBinding) {
        VRInputAction vrinputaction = ClientDataHolderVR.getInstance().vr.getInputAction(keyBinding);
        return Math.abs(vrinputaction.getAxis1DUseTracked());
    }
}
