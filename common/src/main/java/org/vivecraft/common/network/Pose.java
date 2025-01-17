package org.vivecraft.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.common.api_impl.data.VRBodyPartDataImpl;
import org.vivecraft.common.utils.MathUtils;

import static org.vivecraft.common.utils.MathUtils.fromVector3fc;

/**
 * holds a device Pose
 *
 * @param position    position of the device in player local space
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
     *
     * @param buffer buffer to write to
     */
    public void serialize(FriendlyByteBuf buffer) {
        CommonNetworkHelper.serializeF(buffer, this.position);
        CommonNetworkHelper.serialize(buffer, this.orientation);
    }

    /**
     * @param playerPos The current position of the player.
     *
     * @return This Pose as VRBodyPartData for use with the API.
     */
    public VRBodyPartData asBodyPartData(Vec3 playerPos) {
        return new VRBodyPartDataImpl(fromVector3fc(this.position).add(playerPos), fromVector3fc(this.orientation.transform(MathUtils.BACK, new Vector3f())), this.orientation);
    }
}
