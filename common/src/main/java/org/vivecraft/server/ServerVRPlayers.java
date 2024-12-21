package org.vivecraft.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;

import java.util.Map;
import java.util.UUID;

public class ServerVRPlayers {
    /**
     * @param player player to get the ServerVivePlayer object for
     * @return ServerVivePlayer for the player, or {@code null} if there is none
     */
    public static ServerVivePlayer getVivePlayer(ServerPlayer player) {
        return getPlayersWithVivecraft(player.server).get(player.getUUID());
    }

    /**
     * @param player player to check
     * @return if the given player is in VR
     */
    public static boolean isVRPlayer(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        ServerVivePlayer serverVivePlayer = getVivePlayer(player);
        if (serverVivePlayer == null) {
            return false;
        }
        return serverVivePlayer.isVR();
    }

    /**
     * sets the players pose to SWIMMING, when they are roomscale crawling
     * @param player player to set the pose fore
     */
    public static void overridePose(ServerPlayer player) {
        ServerVivePlayer serverVivePlayer = getVivePlayer(player);

        if (serverVivePlayer != null && serverVivePlayer.isVR() && serverVivePlayer.crawling) {
            player.setPose(Pose.SWIMMING);
        }
    }

    /**
     * @param server server to get the vive players for
     * @return map of vive players that are on the server
     */
    public static Map<UUID, ServerVivePlayer> getPlayersWithVivecraft(MinecraftServer server) {
        return ((MinecraftServerExt) server).vivecraft$getPlayersWithVivecraft();
    }
}
