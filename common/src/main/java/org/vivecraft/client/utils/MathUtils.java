package org.vivecraft.client.utils;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class MathUtils {

    public static final Vector3fc FORWARD = new Vector3f(0.0F, 0.0F, 1.0F);
    public static final Vector3fc BACK = new Vector3f(0.0F, 0.0F, -1.0F);
    public static final Vector3fc LEFT = new Vector3f(1.0F, 0.0F, 0.0F);
    public static final Vector3fc RIGHT = new Vector3f(-1.0F, 0.0F, 0.0F);
    public static final Vector3fc UP = new Vector3f(0.0F, 1.0F, 0.0F);
    public static final Vector3fc DOWN = new Vector3f(0.0F, -1.0F, 0.0F);

    public static final Vec3 FORWARD_D = new Vec3(0.0, 0.0, 1.0);
    public static final Vec3 BACK_D = new Vec3(0.0, 0.0, -1.0);
    public static final Vec3 LEFT_D = new Vec3(-1.0, 0.0, 0.0);
    public static final Vec3 RIGHT_D = new Vec3(1.0, 0.0, 0.0);
    public static final Vec3 UP_D = new Vec3(0.0, 1.0, 0.0);
    public static final Vec3 DOWN_D = new Vec3(0.0, -1.0, 0.0);

    /**
     * subtracts {@code b} from {@code a}, anmd returns the result as a Vector3f, should only be used to get local position differences
     * @param a starting vector
     * @param b vector to subtract from {@code a}
     * @return the result of the subtraction as a Vector3f
     */
    public static Vector3f subtractToVector3f(Vec3 a, Vec3 b) {
        return new Vector3f((float) (a.x - b.x), (float) (a.y - b.y), (float) (a.z - b.z));
    }

    public static double lerpMod(double from, double to, double percent, double mod) {
        return Math.abs(to - from) < mod / 2.0D ?
            from + (to - from) * percent :
            from + (to - from - Math.signum(to - from) * mod) * percent;
    }

    /**
     * @return the positive angle difference of the two given angles, in Degrees
     */
    public static float angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360.0F;
        float r = d > 180.0F ? 360.0F - d : d;

        float diff = a - b;
        int sign = (diff >= 0.0F && diff <= 180.0F) || (diff <= -180.0F && diff >= -360.0F) ? 1 : -1;

        return r * sign;
    }

    /**
     * @return the given angle in the 0-360 range
     */
    public static float angleNormalize(float angle) {
        angle = angle % 360.0F;

        if (angle < 0.0F) {
            angle += 360.0F;
        }

        return angle;
    }

    /**
     * lerp for Minecrafts double Vector
     * @param start start point
     * @param end end point
     * @param fraction interpolation amount, 0 will return {@code start}, and 1 return {@code end}
     * @return interpolated vector
     */
    public static Vec3 vecLerp(Vec3 start, Vec3 end, double fraction) {
        double x = start.x + (end.x - start.x) * fraction;
        double y = start.y + (end.y - start.y) * fraction;
        double z = start.z + (end.z - start.z) * fraction;
        return new Vec3(x, y, z);
    }

    public static float applyDeadzone(float axis, float deadzone) {
        if (Math.abs(axis) > deadzone) {
            float scalar = 1.0F / (1.0F - deadzone);
            return (Math.abs(axis) - deadzone) * scalar * Math.signum(axis);
        } else {
            return 0F;
        }
    }

    /**
     * calculates the euler angles of the given Quaternion in the YZX order
     * the returned Vector3f has pitch in X, yaw in Y and roll in Z
     * @param rot quaternion to get the euler angles for
     * @return Euler angles for the given {@code rot}
     */
    public static Vector3f getEulerAnglesYZX(Quaternionf rot) {
        return new Vector3f((float) Math.asin(-2.0F * (rot.y * rot.z - rot.w * rot.x)),
            (float) Math.atan2(2.0F * (rot.x * rot.z + rot.w * rot.y),
                rot.w * rot.w - rot.x * rot.x - rot.y * rot.y + rot.z * rot.z),
            (float) Math.atan2(2.0F * (rot.x * rot.y + rot.w * rot.z),
                rot.w * rot.w - rot.x * rot.x + rot.y * rot.y - rot.z * rot.z));
    }
}
