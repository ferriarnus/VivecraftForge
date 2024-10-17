package org.vivecraft.mixin.server;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.server.*;
import org.vivecraft.server.config.ServerConfig;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin extends ServerCommonPacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    public ServerGamePacketListenerImplMixin(
        MinecraftServer server, Connection connection, CommonListenerCookie cookie)
    {
        super(server, connection, cookie);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vivecraft$addAimFix(CallbackInfo ci) {
        // check is to fix a crash with fake players
        if (this.connection.channel != null && this.connection.channel.pipeline().get("packet_handler") != null) {
            this.connection.channel.pipeline().addBefore("packet_handler", "vr_aim_fix",
                new AimFixHandler(this.connection));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vivecraft$sendVRPlayers(CallbackInfo ci) {

        ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(this.player);

        if (vivePlayer != null) {
            if (this.player.hasDisconnected()) {
                // if they did disconnect remove them
                ServerVRPlayers.getPlayersWithVivecraft(this.player.server).remove(this.player.getUUID());
            } else if (vivePlayer.isVR() && vivePlayer.vrPlayerState != null){
                ServerNetworking.sendVrPlayerStateToClients(vivePlayer);
                if (ServerConfig.DEBUG_PARTICLES.get()) {
                    ServerUtil.debugParticleAxes(vivePlayer);
                }
            }
        }
    }

    @Inject(method = "onDisconnect", at = @At("TAIL"))
    private void vivecraft$doPlayerLeave(CallbackInfo ci) {
        if (ServerConfig.MESSAGES_ENABLED.get()) {
            String message = ServerConfig.MESSAGES_LEAVE_MESSAGE.get();
            if (!message.isEmpty()) {
                this.server.getPlayerList().broadcastSystemMessage(
                    Component.literal(message.formatted(this.player.getScoreboardName())),false);
            }
        }
        // remove player from viveplayer list, when they leave
        ServerVRPlayers.getPlayersWithVivecraft(this.server).remove(this.player.getUUID());
    }
}
