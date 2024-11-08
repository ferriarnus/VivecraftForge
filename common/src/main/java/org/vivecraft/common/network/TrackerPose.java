package org.vivecraft.common.network;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3fc;

/**
 * holds a tracker device Pose
 * @param position position of the device in player local space
 */
public record TrackerPose(Vector3fc position) {

    /**
     * @param buffer buffer to read from
     * @return a Pose read from the given {@code buffer}
     */
    public static TrackerPose deserialize(FriendlyByteBuf buffer) {
        return new TrackerPose(CommonNetworkHelper.deserializeFVec3(buffer));
    }

    /**
     * writes this Pose to the given {@code buffer}
     * @param buffer buffer to write to
     */
    public void serialize(FriendlyByteBuf buffer) {
        CommonNetworkHelper.serializeF(buffer, this.position);
    }
}
