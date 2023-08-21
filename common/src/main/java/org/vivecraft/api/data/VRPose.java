package org.vivecraft.api.data;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Represents the pose data, such as position and rotation, for a given trackable object, such as the HMD or
 * a controller.
 */
public interface VRPose {

    /**
     * @return The position of the device in Minecraft world coordinates.
     */
    Vec3 getPos();

    /**
     * @return The rotation of the device.
     */
    Vec3 getRot();

    /**
     * @return The pitch of the device in radians.
     */
    double getPitch();

    /**
     * @return The yaw of the device in radians.
     */
    double getYaw();

    /**
     * @return The roll of the device in radians.
     */
    double getRoll();

    /**
     * @return The quaternion representing the rotation of the device.
     */
    Quaternionfc getQuaternion();
}
