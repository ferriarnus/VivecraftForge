package org.vivecraft.mixin.client.gui.screens;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.gui.screens.BlockedServerScreen;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

import java.util.Arrays;

@Mixin(JoinMultiplayerScreen.class)
public class JoinMultiplayerScreenMixin extends Screen {
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "join", at = @At("HEAD"), cancellable = true)
    private void vivecraft$checkBlocked(ServerData serverData, CallbackInfo ci) {
        String[] blacklist = ClientDataHolderVR.getInstance().vrSettings.vrServerBlacklist;
        if (Arrays.stream(blacklist).anyMatch(ip -> ip.equals(serverData.ip))) {
            this.minecraft.setScreen(
                new BlockedServerScreen(
                    this,
                    serverData.ip,
                    () -> {
                        ClientDataHolderVR.getInstance().completelyDisabled = true;
                        VRState.VR_ENABLED = false;
                        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false);
                    }));

            ci.cancel();
        }
    }
}
