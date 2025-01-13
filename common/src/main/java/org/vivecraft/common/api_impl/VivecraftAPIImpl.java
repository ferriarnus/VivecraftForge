package org.vivecraft.common.api_impl;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.vivecraft.api.VivecraftAPI;
import org.vivecraft.client.ClientVRPlayers;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.server.ServerVRPlayers;

public final class VivecraftAPIImpl implements VivecraftAPI {

    public static final VivecraftAPIImpl INSTANCE = new VivecraftAPIImpl();

    private VivecraftAPIImpl() {
    }

    @Override
    public boolean isVRPlayer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ServerVRPlayers.isVRPlayer(serverPlayer);
        }

        return ClientVRPlayers.getInstance().isVRPlayer(player);
    }

    @Nullable
    @Override
    public VRPose getVRPose(Player player) {
        if (!isVRPlayer(player)) {
            return null;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return ServerVRPlayers.getVivePlayer(serverPlayer).asVRPose();
        }

        return ClientVRPlayers.getInstance().getRotationsForPlayer(player.getUUID()).asVRPose();
    }
}
