package org.vivecraft.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.vivecraft.common.utils.MathUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.render.RenderPass;

/**
 * holds all data from a player
 * @param seated if the player is in seated mode
 * @param hmd device Pose of the headset
 * @param reverseHands if true, {@code controller0} is the left hand, else {@code controller1} is
 * @param controller0 device Pose of the main interaction hand
 * @param reverseHands1legacy same as {@code reverseHands}, just here for legacy compatibility
 * @param controller1 device Pose of the offhand
 */
public record VrPlayerState(boolean seated, Pose hmd, boolean reverseHands, Pose controller0,
                            boolean reverseHands1legacy, Pose controller1) {

    public static VrPlayerState create(VRPlayer vrPlayer) {
        return new VrPlayerState(
            ClientDataHolderVR.getInstance().vrSettings.seated,
            hmdPose(vrPlayer),
            ClientDataHolderVR.getInstance().vrSettings.reverseHands,
            controllerPose(vrPlayer, 0),
            ClientDataHolderVR.getInstance().vrSettings.reverseHands,
            controllerPose(vrPlayer, 1)
        );
    }

    /**
     * creates the headset Pose object from the client vr data
     * @param vrPlayer object holding the client data
     * @return headset Pose object of the current state
     */
    private static Pose hmdPose(VRPlayer vrPlayer) {
        Vector3f position = MathUtils.subtractToVector3f(
            vrPlayer.vrdata_world_post.getEye(RenderPass.CENTER).getPosition(),
            Minecraft.getInstance().player.position());

        Quaternionf orientation = vrPlayer.vrdata_world_post.hmd.getMatrix().getNormalizedRotation(new Quaternionf());
        return new Pose(position, orientation);
    }

    /**
     * creates the controller Pose object for the specified controller, from the client vr data
     * @param vrPlayer object holding the client data
     * @param controller index of the controller to get the Pose for
     * @return headset Pose object of the current state
     */
    private static Pose controllerPose(VRPlayer vrPlayer, int controller) {
        Vector3f position = MathUtils.subtractToVector3f(
            vrPlayer.vrdata_world_post.getController(controller).getPosition(),
            Minecraft.getInstance().player.position());

        Quaternionf orientation = vrPlayer.vrdata_world_post.getController(controller)
            .getMatrix().getNormalizedRotation(new Quaternionf());
        return new Pose(position, orientation);
    }

    /**
     * @param buffer buffer to read from
     * @return a VrPlayerState read from the given {@code buffer}
     */
    public static VrPlayerState deserialize(FriendlyByteBuf buffer) {
        return new VrPlayerState(
            buffer.readBoolean(),
            Pose.deserialize(buffer),
            buffer.readBoolean(),
            Pose.deserialize(buffer),
            buffer.readBoolean(),
            Pose.deserialize(buffer)
        );
    }

    /**
     * writes this VrPlayerState to the given {@code buffer}
     * @param buffer buffer to write to
     */
    public void serialize(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.seated);
        this.hmd.serialize(buffer);
        buffer.writeBoolean(this.reverseHands);
        this.controller0.serialize(buffer);
        buffer.writeBoolean(this.reverseHands);
        this.controller1.serialize(buffer);
    }
}
