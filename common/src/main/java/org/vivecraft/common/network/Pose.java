package org.vivecraft.common.network;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;

/**
 * holds a device Pose
 * @param position position of the device in player local space
 * @param orientation orientation of the device in world space
 */
public record Pose(Vector3fc position, Quaternionfc orientation) {

    /**
     * @param buffer buffer to read from
     * @return a Pose read from the given {@code buffer}
     */
    public static Pose deserialize(FriendlyByteBuf buffer) {
        return new Pose(
            CommonNetworkHelper.deserializeFVec3(buffer),
            CommonNetworkHelper.deserializeVivecraftQuaternion(buffer)
        );
    }

    /**
     * writes this Pose to the given {@code buffer}
     * @param buffer buffer to write to
     */
    public void serialize(FriendlyByteBuf buffer) {
        CommonNetworkHelper.serializeF(buffer, this.position);
        CommonNetworkHelper.serialize(buffer, this.orientation);
    }
}
