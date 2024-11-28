package org.vivecraft.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.server.config.ServerConfig;

public class AimFixHandler extends ChannelInboundHandlerAdapter {
    private final Connection netManager;

    public AimFixHandler(Connection netManager) {
        this.netManager = netManager;
    }

    /**
     * checks if the {@code msg}  uses the players aim, and changes it to the right position before handling
     * @param ctx context when not handling the message
     * @param msg Packet to handle
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ServerPlayer serverPlayer = ((ServerGamePacketListenerImpl) this.netManager.getPacketListener()).player;
        boolean isCapturedPacket = msg instanceof ServerboundUseItemPacket ||
            msg instanceof ServerboundUseItemOnPacket ||
            msg instanceof ServerboundPlayerActionPacket;

        if (!ServerVRPlayers.isVRPlayer(serverPlayer) || !isCapturedPacket || serverPlayer.getServer() == null) {
            // we don't need to handle this packet, just defer to the next handler in the pipeline
            ctx.fireChannelRead(msg);
            return;
        }

        serverPlayer.getServer().submit(() -> {
            // Save all the current orientation data
            Vec3 pos = serverPlayer.position();
            Vec3 prevPos = new Vec3(serverPlayer.xo, serverPlayer.yo, serverPlayer.zo);
            float xRot = serverPlayer.getXRot();
            float yRot = serverPlayer.getYRot();
            float yHeadRot = serverPlayer.yHeadRot;
            float prevXRot = serverPlayer.xRotO;
            float prevYRot = serverPlayer.yRotO;
            float prevYHeadRot = serverPlayer.yHeadRotO;
            float eyeHeight = serverPlayer.getEyeHeight();

            ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);

            Vec3 aimPos = null;
            // Check again in case of race condition
            if (vivePlayer != null && vivePlayer.isVR()) {
                aimPos = vivePlayer.getControllerPos(0, true);
                Vec3 dir = vivePlayer.getControllerDir(0);

                // Inject our custom orientation data
                serverPlayer.setPosRaw(aimPos.x, aimPos.y, aimPos.z);
                serverPlayer.xo = aimPos.x;
                serverPlayer.yo = aimPos.y;
                serverPlayer.zo = aimPos.z;
                serverPlayer.setXRot((float) Math.toDegrees(Math.asin(-dir.y)));
                serverPlayer.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                serverPlayer.xRotO = serverPlayer.getXRot();
                serverPlayer.yRotO = serverPlayer.yHeadRotO = serverPlayer.yHeadRot = serverPlayer.getYRot();
                // non 0 to avoid divisions by 0
                serverPlayer.eyeHeight = 0.0001F;

                // Set up offset to fix relative positions
                vivePlayer.offset = pos.subtract(aimPos);
                if (ServerConfig.DEBUG.get()) {
                    ServerNetworking.LOGGER.info("Vivecraft: AimFix: {} {} {}, {} {}", aimPos.x, aimPos.y, aimPos.z,
                        Math.toDegrees(Math.asin(-dir.y)), Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                }
            }

            // Call the packet handler directly
            // This is several implementation details that we have to replicate
            try {
                if (this.netManager.isConnected()) {
                    try {
                        ((Packet) msg).handle(this.netManager.getPacketListener());
                    } catch (RunningOnDifferentThreadException ignored) {
                        // Apparently might get thrown and can be ignored
                    }
                }
            } finally {
                // Vanilla uses SimpleChannelInboundHandler, which automatically releases
                // by default, so we're expected to release the packet once we're done.
                ReferenceCountUtil.release(msg);
            }

            // if the packed changed the player position, use that
            if ((aimPos != null && !serverPlayer.position().equals(aimPos)) || (aimPos == null && !serverPlayer.position().equals(pos))) {
                pos = serverPlayer.position();
                if (ServerConfig.DEBUG.get()) {
                    ServerNetworking.LOGGER.info("Vivecraft: AimFix moved Player to: {} {} {}", pos.x, pos.y, pos.z);
                }
            }

            // Restore the original orientation data
            serverPlayer.setPosRaw(pos.x, pos.y, pos.z);
            serverPlayer.xo = prevPos.x;
            serverPlayer.yo = prevPos.y;
            serverPlayer.zo = prevPos.z;
            serverPlayer.setXRot(xRot);
            serverPlayer.setYRot(yRot);
            serverPlayer.yHeadRot = yHeadRot;
            serverPlayer.xRotO = prevXRot;
            serverPlayer.yRotO = prevYRot;
            serverPlayer.yHeadRotO = prevYHeadRot;
            serverPlayer.eyeHeight = eyeHeight;

            // Reset offset
            if (vivePlayer != null) {
                vivePlayer.offset = new Vec3(0.0D, 0.0D, 0.0D);
            }
        });
    }
}
