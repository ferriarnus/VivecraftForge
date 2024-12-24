package org.vivecraft.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.settings.AutoCalibration;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.CommonDataHolder;
import org.vivecraft.common.VRServerPerms;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.common.network.Limb;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.common.network.packet.c2s.*;
import org.vivecraft.common.network.packet.s2c.*;

import java.util.Map;

public class ClientNetworking {

    public static boolean DISPLAYED_CHAT_MESSAGE = false;
    public static boolean DISPLAYED_CHAT_WARNING = false;

    public static boolean SERVER_HAS_VIVECRAFT = false;

    public static boolean SERVER_WANTS_DATA = false;
    public static boolean SERVER_SUPPORTS_DIRECT_TELEPORT = false;
    public static boolean SERVER_ALLOWS_CLIMBEY = false;
    public static boolean SERVER_ALLOWS_CRAWLING = false;
    public static boolean SERVER_ALLOWS_VR_SWITCHING = false;
    public static boolean SERVER_ALLOWS_DUAL_WIELDING = false;

    // assume a legacy server by default, to not send invalid packets
    public static int USED_NETWORK_VERSION = CommonNetworkHelper.NETWORK_VERSION_LEGACY;
    private static float WORLDSCALE_LAST = 0.0F;
    private static float HEIGHT_LAST = 0.0F;
    private static float CAPTURED_YAW;
    private static float CAPTURED_PITCH;
    private static boolean OVERRIDE_ACTIVE;
    public static Limb LAST_SENT_LIMB = Limb.MAIN_HAND;

    public static boolean NEEDS_RESET = true;

    public static void resetServerSettings() {
        WORLDSCALE_LAST = 0.0F;
        HEIGHT_LAST = 0.0F;
        SERVER_HAS_VIVECRAFT = false;
        SERVER_WANTS_DATA = false;
        SERVER_SUPPORTS_DIRECT_TELEPORT = false;
        SERVER_ALLOWS_CLIMBEY = false;
        SERVER_ALLOWS_CRAWLING = false;
        SERVER_ALLOWS_VR_SWITCHING = false;
        SERVER_ALLOWS_DUAL_WIELDING = false;
        USED_NETWORK_VERSION = CommonNetworkHelper.NETWORK_VERSION_LEGACY;

        // clear VR player data
        ClientVRPlayers.clear();
        // clear teleport
        VRServerPerms.INSTANCE.setTeleportSupported(false);
        if (VRState.VR_INITIALIZED) {
            ClientDataHolderVR.getInstance().vrPlayer.setTeleportOverride(false);
        }
        // clear server overrides
        ClientDataHolderVR.getInstance().vrSettings.overrides.resetAll();
    }

    public static void sendVersionInfo() {
        // send version string, with currently running
        if (!ClientDataHolderVR.getInstance().completelyDisabled) {
            Minecraft.getInstance().getConnection().send(createServerPacket(
                new VersionPayloadC2S(
                    CommonDataHolder.getInstance().versionIdentifier,
                    VRState.VR_RUNNING,
                    CommonNetworkHelper.MAX_SUPPORTED_NETWORK_VERSION,
                    CommonNetworkHelper.MIN_SUPPORTED_NETWORK_VERSION)));
        }
    }

    public static void sendVRPlayerPositions(VRPlayer vrPlayer) {
        if (!SERVER_WANTS_DATA || Minecraft.getInstance().getConnection() == null) {
            return;
        }

        float worldScale = ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_post.worldScale;

        if (worldScale != WORLDSCALE_LAST) {
            sendServerPacket(new WorldScalePayloadC2S(worldScale));

            WORLDSCALE_LAST = worldScale;
        }

        float userHeight = AutoCalibration.getPlayerHeight();

        if (userHeight != HEIGHT_LAST) {
            sendServerPacket(new HeightPayloadC2S(userHeight / AutoCalibration.DEFAULT_HEIGHT));

            HEIGHT_LAST = userHeight;
        }

        var vrPlayerState = VrPlayerState.create(vrPlayer);

        if (USED_NETWORK_VERSION != CommonNetworkHelper.NETWORK_VERSION_LEGACY) {
            sendServerPacket(new VRPlayerStatePayloadC2S(vrPlayerState));
        } else {
            sendLegacyPackets(vrPlayerState);
        }
        ClientVRPlayers.getInstance()
            .update(Minecraft.getInstance().player.getGameProfile().getId(), vrPlayerState, worldScale,
                userHeight / AutoCalibration.DEFAULT_HEIGHT, true);
    }

    /**
     * Sends the given {@code payload} to the server, but only if the server sent that it has vivecraft
     * @param payload Payload to send
     */
    public static void sendServerPacket(VivecraftPayloadC2S payload) {
        if (Minecraft.getInstance().getConnection() != null && SERVER_HAS_VIVECRAFT) {
            Minecraft.getInstance().getConnection().send(createServerPacket(payload));
        }
    }

    public static Packet<?> createServerPacket(VivecraftPayloadC2S payload) {
        return Xplat.getC2SPacket(payload);
    }

    public static void sendLegacyPackets(VrPlayerState vrPlayerState) {
        // main controller packet
        sendServerPacket(new LegacyController0DataPayloadC2S(ClientDataHolderVR.getInstance().vrSettings.reverseHands,
            vrPlayerState.mainHand()));

        // offhand controller packet
        sendServerPacket(new LegacyController1DataPayloadC2S(ClientDataHolderVR.getInstance().vrSettings.reverseHands,
            vrPlayerState.offHand()));

        // hmd packet
        sendServerPacket(
            new LegacyHeadDataPayloadC2S(ClientDataHolderVR.getInstance().vrSettings.seated, vrPlayerState.hmd()));
    }

    // ServerSetting override checks

    public static boolean isThirdPersonItems() {
        return ClientDataHolderVR.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.THIRDPERSON_ITEMTRANSFORMS).getBoolean();
    }

    public static boolean isThirdPersonItemsCustom() {
        return ClientDataHolderVR.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.THIRDPERSON_ITEMTRANSFORMS_CUSTOM).getBoolean();
    }

    public static boolean isLimitedSurvivalTeleport() {
        return ClientDataHolderVR.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.LIMIT_TELEPORT).getBoolean();
    }

    public static boolean supportsReversedBow() {
        // old plugins hardcode the hand order
        return USED_NETWORK_VERSION >= CommonNetworkHelper.NETWORK_VERSION_DUAL_WIELDING || !SERVER_HAS_VIVECRAFT;
    }

    public static int getTeleportUpLimit() {
        return ClientDataHolderVR.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.TELEPORT_UP_LIMIT).getInt();
    }

    public static int getTeleportDownLimit() {
        return ClientDataHolderVR.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.TELEPORT_DOWN_LIMIT).getInt();
    }

    public static int getTeleportHorizLimit() {
        return ClientDataHolderVR.getInstance().vrSettings.overrides.getSetting(VRSettings.VrOptions.TELEPORT_HORIZ_LIMIT).getInt();
    }

    public static void sendActiveHand(InteractionHand hand) {
        if (SERVER_WANTS_DATA) {
            sendActiveLimb(hand == InteractionHand.MAIN_HAND ? Limb.MAIN_HAND : Limb.OFF_HAND);
        }
    }

    public static void sendActiveLimb(Limb limb) {
        if (SERVER_WANTS_DATA) {
            // only send if the hand is different from last time, don't need to spam packets
            if (limb != LAST_SENT_LIMB) {
                sendServerPacket(new ActiveLimbPayloadC2S(limb));
                LAST_SENT_LIMB = limb;
            }
        }
    }

    public static void overridePose(LocalPlayer player) {
        if (ClientDataHolderVR.getInstance().crawlTracker.crawling) {
            player.setPose(Pose.SWIMMING);
        }
    }

    public static void overrideLook(Player player, Vec3 view) {
        if (SERVER_WANTS_DATA) return; // shouldn't be needed, don't tease the anti-cheat.

        CAPTURED_PITCH = player.getXRot();
        CAPTURED_YAW = player.getYRot();
        float pitch = (float) Math.toDegrees(Math.asin(-view.y / view.length()));
        float yaw = (float) Math.toDegrees(Math.atan2(-view.x, view.z));
        ((LocalPlayer) player).connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround()));
        OVERRIDE_ACTIVE = true;
    }

    public static void restoreLook(Player player) {
        if (!SERVER_WANTS_DATA) {
            if (OVERRIDE_ACTIVE) {
                ((LocalPlayer) player).connection.send(new ServerboundMovePlayerPacket.Rot(CAPTURED_YAW, CAPTURED_PITCH, player.onGround()));
                OVERRIDE_ACTIVE = false;
            }
        }
    }

    public static void handlePacket(VivecraftPayloadS2C s2cPayload) {
        if (s2cPayload == null) return;
        ClientDataHolderVR dataholder = ClientDataHolderVR.getInstance();
        Minecraft mc = Minecraft.getInstance();
        switch (s2cPayload.payloadId()) {
            case VERSION -> {
                SERVER_HAS_VIVECRAFT = true;
                VRServerPerms.INSTANCE.setTeleportSupported(true);
                if (VRState.VR_INITIALIZED) {
                    dataholder.vrPlayer.teleportWarning = false;
                    dataholder.vrPlayer.vrSwitchWarning = true;
                }
                if (!ClientNetworking.DISPLAYED_CHAT_MESSAGE &&
                    (dataholder.vrSettings.showServerPluginMessage == VRSettings.ChatServerPluginMessage.ALWAYS ||
                        (dataholder.vrSettings.showServerPluginMessage ==
                            VRSettings.ChatServerPluginMessage.SERVER_ONLY && !Minecraft.getInstance().isLocalServer()
                        )
                    ))
                {
                    ClientNetworking.DISPLAYED_CHAT_MESSAGE = true;
                    mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.serverplugin",
                        ((VersionPayloadS2C) s2cPayload).version()));
                }
                if (VRState.VR_ENABLED && dataholder.vrSettings.manualCalibration == -1.0F && !dataholder.vrSettings.seated) {
                    mc.gui.getChat().addMessage(Component.translatable("vivecraft.messages.calibrateheight"));
                }
            }
            case IS_VR_ACTIVE -> {
                VRActivePayloadS2C packet = (VRActivePayloadS2C) s2cPayload;
                if (!packet.vr()) {
                    ClientVRPlayers.getInstance().disableVR(packet.playerID());
                }
            }
            case REQUESTDATA -> ClientNetworking.SERVER_WANTS_DATA = true;
            case CLIMBING -> {
                ClimbingPayloadS2C packet = (ClimbingPayloadS2C) s2cPayload;
                ClientNetworking.SERVER_ALLOWS_CLIMBEY = packet.allowed();
                dataholder.climbTracker.serverBlockmode = packet.blockmode();
                dataholder.climbTracker.blocklist.clear();

                if (packet.blocks() != null) {
                    for (String blockId : packet.blocks()) {
                        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(blockId));

                        // if the block is not there AIR is returned
                        if (block != Blocks.AIR) {
                            dataholder.climbTracker.blocklist.add(block);
                        }
                    }
                }
            }
            case TELEPORT -> ClientNetworking.SERVER_SUPPORTS_DIRECT_TELEPORT = true;
            case UBERPACKET -> {
                UberPacketPayloadS2C packet = (UberPacketPayloadS2C) s2cPayload;
                ClientVRPlayers.getInstance().update(packet.playerID(), packet.state(), packet.worldScale(), packet.heightScale());
            }
            case SETTING_OVERRIDE -> {
                for (Map.Entry<String, String> override : ((SettingOverridePayloadS2C) s2cPayload).overrides().entrySet()) {
                    String[] split = override.getKey().split("\\.", 2);

                    if (dataholder.vrSettings.overrides.hasSetting(split[0])) {
                        VRSettings.ServerOverrides.Setting setting = dataholder.vrSettings.overrides.getSetting(split[0]);

                        try {
                            if (split.length > 1) {
                                switch (split[1]) {
                                    case "min" -> setting.setValueMin(Float.parseFloat(override.getValue()));
                                    case "max"-> setting.setValueMax(Float.parseFloat(override.getValue()));
                                }
                            } else {
                                Object origValue = setting.getOriginalValue();

                                if (origValue instanceof Boolean) {
                                    setting.setValue(override.getValue().equals("true"));
                                } else if (origValue instanceof Integer || origValue instanceof Byte || origValue instanceof Short) {
                                    setting.setValue(Integer.parseInt(override.getValue()));
                                } else if (origValue instanceof Float || origValue instanceof Double) {
                                    setting.setValue(Float.parseFloat(override.getValue()));
                                } else {
                                    setting.setValue(override.getValue());
                                }
                            }

                            VRSettings.LOGGER.info("Vivecraft: Server setting override: {}={}", override.getKey(), override.getValue());
                        } catch (Exception exception) {
                            VRSettings.LOGGER.error("Vivecraft: error parsing server setting override: ", exception);
                        }
                    }
                }
            }
            case CRAWL -> ClientNetworking.SERVER_ALLOWS_CRAWLING = true;
            case NETWORK_VERSION ->
                ClientNetworking.USED_NETWORK_VERSION = ((NetworkVersionPayloadS2C) s2cPayload).version();
            case VR_SWITCHING -> {
                ClientNetworking.SERVER_ALLOWS_VR_SWITCHING = ((VRSwitchingPayloadS2C) s2cPayload).allowed();
                if (VRState.VR_INITIALIZED) {
                    if (!ClientNetworking.SERVER_ALLOWS_VR_SWITCHING) {
                        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("vivecraft.messages.novrhotswitching"));
                    }
                    dataholder.vrPlayer.vrSwitchWarning = false;
                }
            }
            case DUAL_WIELDING ->
                ClientNetworking.SERVER_ALLOWS_DUAL_WIELDING = ((DualWieldingPayloadS2C) s2cPayload).allowed();
        }
    }
}
