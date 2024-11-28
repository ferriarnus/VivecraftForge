package org.vivecraft.mixin.client.main;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;

@Mixin(Main.class)
public class MainMixin {

    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void vivecraft$options(String[] strings, CallbackInfo ci) {
        ClientDataHolderVR.KIOSK = System.getProperty("kiosk", "false").equals("true");

        if (ClientDataHolderVR.KIOSK) {
            VRSettings.LOGGER.info("Vivecraft: Setting kiosk");
            ClientDataHolderVR.VIEW_ONLY = System.getProperty("viewonly", "false").equals("true");

            if (ClientDataHolderVR.VIEW_ONLY) {
                VRSettings.LOGGER.info("Vivecraft: Setting viewonly");
            }
        }

        ClientDataHolderVR.KAT_VR = System.getProperty("katvr", "false").equals("true");
        ClientDataHolderVR.INFINADECK = System.getProperty("infinadeck", "false").equals("true");
    }
}
