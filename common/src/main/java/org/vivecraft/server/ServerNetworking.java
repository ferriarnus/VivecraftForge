package org.vivecraft.server;

import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivecraft.client.Xplat;
import org.vivecraft.common.CommonDataHolder;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.common.network.packet.PayloadIdentifier;
import org.vivecraft.common.network.packet.c2s.*;
import org.vivecraft.common.network.packet.s2c.*;
import org.vivecraft.mixin.server.ChunkMapAccessor;
import org.vivecraft.mixin.server.TrackedEntityAccessor;
import org.vivecraft.server.config.ClimbeyBlockmode;
import org.vivecraft.server.config.ServerConfig;

import java.util.*;
import java.util.function.Consumer;

public class ServerNetworking {

    // temporarily stores the packets from legacy clients to assemble a complete VrPlayerState
    private static final Map<UUID, Map<PayloadIdentifier, VivecraftPayloadC2S>> LEGACY_DATA_MAP = new HashMap<>();

    /**
     * logger for messages from the server
     */
    public static final Logger LOGGER = LoggerFactory.getLogger("VivecraftServer");

    /**
     * handles a {@link VivecraftPayloadC2S} sent to the server
     * @param c2sPayload payload that needs to be handled
     * @param player ServerPlayer that sent the packet
     * @param packetConsumer consumer to send packets back with
     */
    public static void handlePacket(
        VivecraftPayloadC2S c2sPayload, ServerPlayer player, Consumer<VivecraftPayloadS2C> packetConsumer)
    {
        ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(player);

        // clients are expected to send a VERSION packet first
        if (vivePlayer == null && c2sPayload.payloadId() != PayloadIdentifier.VERSION) {
            return;
        }

        // the player object changes in some circumstances, like respawning, so need to make sure it's up to date
        if (vivePlayer != null) {
            vivePlayer.player = player;
        }

        switch (c2sPayload.payloadId()) {
            case VERSION -> {
                // Vivecraft client connected, send server settings
                vivePlayer = new ServerVivePlayer(player);

                VersionPayloadC2S payload = (VersionPayloadC2S) c2sPayload;

                if (ServerConfig.DEBUG.get()) {
                    LOGGER.info("Vivecraft: player '{}' joined with {}", player.getName().getString(),
                        payload.version());
                }

                if (!payload.legacy()) {
                    // check if client supports a supported version
                    if (CommonNetworkHelper.MIN_SUPPORTED_NETWORK_VERSION <= payload.maxVersion() &&
                        payload.minVersion() <= CommonNetworkHelper.MAX_SUPPORTED_NETWORK_VERSION)
                    {
                        vivePlayer.networkVersion = Math.min(payload.maxVersion(),
                            CommonNetworkHelper.MAX_SUPPORTED_NETWORK_VERSION);
                        if (ServerConfig.DEBUG.get()) {
                            LOGGER.info("Vivecraft: {} networking supported, using version {}",
                                player.getName().getString(), vivePlayer.networkVersion);
                        }
                    } else {
                        // unsupported version, send notification, and disregard
                        player.sendSystemMessage(
                            Component.literal("Unsupported vivecraft version, VR features will not work"));
                        if (ServerConfig.DEBUG.get()) {
                            LOGGER.info(
                                "Vivecraft: {} networking not supported. client range [{},{}], server range [{},{}]",
                                player.getScoreboardName(),
                                payload.minVersion(),
                                payload.maxVersion(),
                                CommonNetworkHelper.MIN_SUPPORTED_NETWORK_VERSION,
                                CommonNetworkHelper.MAX_SUPPORTED_NETWORK_VERSION);
                        }
                        return;
                    }
                } else {
                    // client didn't send a version, so it's a legacy client
                    vivePlayer.networkVersion = -1;
                    if (ServerConfig.DEBUG.get()) {
                        LOGGER.info("Vivecraft: {} using legacy networking", player.getScoreboardName());
                    }
                }

                vivePlayer.setVR(payload.vr());

                ServerVRPlayers.getPlayersWithVivecraft(player.server).put(player.getUUID(), vivePlayer);

                packetConsumer.accept(new VersionPayloadS2C(CommonDataHolder.getInstance().versionIdentifier));
                packetConsumer.accept(new RequestDataPayloadS2C());

                // send server settings
                if (ServerConfig.CLIMBEY_ENABLED.get()) {
                    packetConsumer.accept(getClimbeyServerPayload());
                }

                if (ServerConfig.TELEPORT_ENABLED.get()) {
                    packetConsumer.accept(new TeleportPayloadS2C());
                }
                if (ServerConfig.TELEPORT_LIMITED_SURVIVAL.get()) {
                    packetConsumer.accept(new SettingOverridePayloadS2C(Map.of(
                        "limitedTeleport", "true",
                        "teleportLimitUp", String.valueOf(ServerConfig.TELEPORT_UP_LIMIT.get()),
                        "teleportLimitDown", String.valueOf(ServerConfig.TELEPORT_DOWN_LIMIT.get()),
                        "teleportLimitHoriz", String.valueOf(ServerConfig.TELEPORT_HORIZONTAL_LIMIT.get())
                    )));
                }

                if (ServerConfig.WORLDSCALE_LIMITED.get()) {
                    packetConsumer.accept(new SettingOverridePayloadS2C(Map.of(
                        "worldScale.min", String.valueOf(ServerConfig.WORLDSCALE_MIN.get()),
                        "worldScale.max", String.valueOf(ServerConfig.WORLDSCALE_MAX.get())
                    )));
                }

                if (ServerConfig.FORCE_THIRD_PERSON_ITEMS.get()) {
                    packetConsumer.accept(new SettingOverridePayloadS2C(Map.of(
                        "thirdPersonItems", "true"
                    )));
                }

                if (ServerConfig.FORCE_THIRD_PERSON_ITEMS_CUSTOM.get()) {
                    packetConsumer.accept(new SettingOverridePayloadS2C(Map.of(
                        "thirdPersonItemsCustom", "true"
                    )));
                }

                if (ServerConfig.CRAWLING_ENABLED.get()) {
                    packetConsumer.accept(new CrawlPayloadS2C());
                }

                // send if hotswitching is allowed
                packetConsumer.accept(
                    new VRSwitchingPayloadS2C(ServerConfig.VR_SWITCHING_ENABLED.get() && !ServerConfig.VR_ONLY.get()));

                packetConsumer.accept(new NetworkVersionPayloadS2C(vivePlayer.networkVersion));
            }
            case IS_VR_ACTIVE -> {
                VRActivePayloadC2S payload = (VRActivePayloadC2S) c2sPayload;
                if (vivePlayer.isVR() == payload.vr()) {
                    break;
                }
                vivePlayer.setVR(!vivePlayer.isVR());
                if (!vivePlayer.isVR()) {
                    // send all nearby players that the state changed
                    // this is only needed for OFF, to delete the clientside vr player state
                    sendPacketToTrackingPlayers(vivePlayer, new VRActivePayloadS2C(false, player.getUUID()));
                }
            }
            case DRAW -> vivePlayer.draw = ((DrawPayloadC2S)c2sPayload).draw();
            case VR_PLAYER_STATE -> vivePlayer.vrPlayerState = ((VRPlayerStatePayloadC2S)c2sPayload).playerState();
            case WORLDSCALE -> vivePlayer.worldScale = ((WorldScalePayloadC2S) c2sPayload).worldScale();
            case HEIGHT -> vivePlayer.heightScale = ((HeightPayloadC2S) c2sPayload).heightScale();
            case TELEPORT -> {
                TeleportPayloadC2S payload = (TeleportPayloadC2S) c2sPayload;
                player.absMoveTo(payload.x(), payload.y(), payload.z(), player.getYRot(), player.getXRot());
            }
            case CLIMBING -> {
                player.fallDistance = 0.0F;
                player.connection.aboveGroundTickCount = 0;
            }
            case ACTIVEHAND ->
                vivePlayer.activeHand = vivePlayer.isSeated() ? 0 : ((ActiveHandPayloadC2S) c2sPayload).hand();
            case CRAWL -> {
                vivePlayer.crawling = ((CrawlPayloadC2S) c2sPayload).crawling();
                if (vivePlayer.crawling) {
                    player.setPose(Pose.SWIMMING);
                }
            }
            // legacy support
            case CONTROLLER0DATA, CONTROLLER1DATA, HEADDATA -> {
                Map<PayloadIdentifier, VivecraftPayloadC2S> playerData;
                if ((playerData = LEGACY_DATA_MAP.get(player.getUUID())) == null) {
                    playerData = new HashMap<>();
                    LEGACY_DATA_MAP.put(player.getUUID(), playerData);
                }
                // keep the payload around
                playerData.put(c2sPayload.payloadId(), c2sPayload);

                if (playerData.size() == 3) {
                    // we have all data
                    LegacyController0DataPayloadC2S controller0Data = (LegacyController0DataPayloadC2S) playerData
                        .get(PayloadIdentifier.CONTROLLER0DATA);
                    LegacyController1DataPayloadC2S controller1Data = (LegacyController1DataPayloadC2S) playerData
                        .get(PayloadIdentifier.CONTROLLER1DATA);
                    LegacyHeadDataPayloadC2S headData = (LegacyHeadDataPayloadC2S) playerData
                        .get(PayloadIdentifier.HEADDATA);

                    vivePlayer.vrPlayerState = new VrPlayerState(
                        headData.seated(), // isSeated
                        headData.hmdPose(), // head pose
                        controller0Data.reverseHands(), // reverseHands 0
                        controller0Data.controller1Pose(), // controller0 pose
                        controller1Data.reverseHands(), // reverseHands 1
                        controller1Data.controller0Pose()); // controller1 pose

                    LEGACY_DATA_MAP.remove(player.getUUID());
                }
            }
            default -> throw new IllegalStateException("Vivecraft: got unexpected packet on server: " + c2sPayload.payloadId());
        }
    }

    /**
     * @return CLIMBING payload holding blockmode and list of blocks
     */
    public static VivecraftPayloadS2C getClimbeyServerPayload() {
        List<String> blocks = null;
        if (ServerConfig.CLIMBEY_BLOCKMODE.get() != ClimbeyBlockmode.DISABLED) {
            blocks = new ArrayList<>();
            for (String block : ServerConfig.CLIMBEY_BLOCKLIST.get()) {
                try {
                    Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(block));
                    // only send valid blocks
                    if (b != Blocks.AIR) {
                        blocks.add(block);
                    }
                } catch (ResourceLocationException ignore) {}
            }
        }
        return new ClimbingPayloadS2C(true, ServerConfig.CLIMBEY_BLOCKMODE.get(), blocks);
    }

    /**
     * send the players VR data to all other players that can see them
     * @param vivePlayer player to send the VR data for
     */
    public static void sendVrPlayerStateToClients(ServerVivePlayer vivePlayer) {
        sendPacketToTrackingPlayers(vivePlayer,
            new UberPacketPayloadS2C(vivePlayer.player.getUUID(), vivePlayer.vrPlayerState, vivePlayer.worldScale,
                vivePlayer.heightScale));
    }

    /**
     * gets all players that can see {@code player}
     * @param player ServerPlayer to check
     * @return unmodifiableSet set of all other players that can see {@code player}
     */
    public static Set<ServerPlayerConnection> getTrackingPlayers(ServerPlayer player) {
        ChunkMap chunkMap = player.serverLevel().getChunkSource().chunkMap;
        TrackedEntityAccessor playerTracker = ((ChunkMapAccessor) chunkMap).getTrackedEntities().get(player.getId());
        return playerTracker != null ? Collections.unmodifiableSet(playerTracker.getPlayersTracking()) : Collections.emptySet();
    }

    /**
     * sends a packet to all players that can see {@code vivePlayer}
     * @param vivePlayer player that needs to be seen to get the packet
     * @param payload payload to send
     */
    private static void sendPacketToTrackingPlayers(ServerVivePlayer vivePlayer, VivecraftPayloadS2C payload) {
        Packet<?> packet = Xplat.getS2CPacket(payload);

        Map<UUID, ServerVivePlayer> vivePlayers = ServerVRPlayers.getPlayersWithVivecraft(vivePlayer.player.server);
        for (var trackedPlayer : getTrackingPlayers(vivePlayer.player)) {
            if (!vivePlayers.containsKey(trackedPlayer.getPlayer().getUUID()) ||
                trackedPlayer.getPlayer() == vivePlayer.player)
            {
                continue;
            }
            trackedPlayer.send(packet);
        }
    }
}
