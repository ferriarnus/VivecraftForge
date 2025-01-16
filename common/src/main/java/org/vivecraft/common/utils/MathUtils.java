package org.vivecraft.common.utils;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.lang.Math;

public class MathUtils {

    public static final Vector3fc FORWARD = new Vector3f(0.0F, 0.0F, 1.0F);
    public static final Vector3fc BACK = new Vector3f(0.0F, 0.0F, -1.0F);
    public static final Vector3fc LEFT = new Vector3f(1.0F, 0.0F, 0.0F);
    public static final Vector3fc RIGHT = new Vector3f(-1.0F, 0.0F, 0.0F);
    public static final Vector3fc UP = new Vector3f(0.0F, 1.0F, 0.0F);
    public static final Vector3fc DOWN = new Vector3f(0.0F, -1.0F, 0.0F);

    public static final Vec3 FORWARD_D = new Vec3(0.0, 0.0, 1.0);
    public static final Vec3 BACK_D = new Vec3(0.0, 0.0, -1.0);
    public static final Vec3 LEFT_D = new Vec3(1.0, 0.0, 0.0);
    public static final Vec3 RIGHT_D = new Vec3(-1.0, 0.0, 0.0);
    public static final Vec3 UP_D = new Vec3(0.0, 1.0, 0.0);
    public static final Vec3 DOWN_D = new Vec3(0.0, -1.0, 0.0);

    /**
     * subtracts {@code b} from {@code a}, and returns the result as a Vector3f, should only be used to get local position differences
     *
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
     * @return wraps a radians value to be in the range -PI to +PI
     */
    public static float wrapRadians(float value) {
        float wrapped = value % Mth.TWO_PI;
        if (wrapped >= Mth.PI) {
            wrapped -= Mth.TWO_PI;
        }

        if (wrapped < -Mth.PI) {
            wrapped += Mth.TWO_PI;
        }

        return wrapped;
    }

    /**
     * lerps the radians rotation from start to end
     *
     * @return lerped rotation
     */
    public static float rotLerpRad(float delta, float start, float end) {
        return start + delta * wrapRadians(end - start);
    }

    /**
     * lerp for Minecrafts double Vector
     *
     * @param start    start point
     * @param end      end point
     * @param fraction interpolation amount, 0 will return {@code start}, and 1 return {@code end}
     * @return interpolated vector
     */
    public static Vec3 vecDLerp(Vec3 start, Vec3 end, double fraction) {
        double x = start.x + (end.x - start.x) * fraction;
        double y = start.y + (end.y - start.y) * fraction;
        double z = start.z + (end.z - start.z) * fraction;
        return new Vec3(x, y, z);
    }

    /**
     * lerp {@code start} to the given end point
     *
     * @param start    start point
     * @param endX     X of the end point
     * @param endY     Y of the end point
     * @param endZ     Z of the end point
     * @param fraction interpolation amount, 0 will return {@code start}, and 1 return {@code end}
     * @return {@code start} containing the lerped vector
     */
    public static Vector3f vecLerp(Vector3f start, float endX, float endY, float endZ, float fraction) {
        return start.set(
            start.x + (endX - start.x) * fraction,
            start.y + (endY - start.y) * fraction,
            start.z + (endZ - start.z) * fraction
        );
    }

    public static float applyDeadzone(float axis, float deadzone) {
        if (Math.abs(axis) > deadzone) {
            float scalar = 1.0F / (1.0F - deadzone);
            return (Math.abs(axis) - deadzone) * scalar * Math.signum(axis);
        } else {
            return 0F;
        }
    }

    public static float normalizedDotXZ(Vector3fc a, Vector3fc b) {
        return (a.x() * b.x() + a.z() * b.z()) /
            (float) Math.sqrt((a.x() * a.x() + a.z() * a.z()) * (b.x() * b.x() + b.z() * b.z()));
    }

    /**
     * rotates the given vector around the X axis, based on the provided sin and cos
     *
     * @param v   Vector to rotate
     * @param sin precomputed sinus of the rotation
     * @param cos precomputed cosinus of the rotation
     */
    public static void rotateX(Vector3f v, float sin, float cos) {
        float ogY = v.y;
        v.y = ogY * cos - v.z * sin;
        v.z = ogY * sin + v.z * cos;
    }

    /**
     * calculates the euler angles of the given Quaternion in the YZX order
     * the returned Vector3f has pitch in X, yaw in Y and roll in Z
     *
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

    /**
     * fixed version of {@link Quaternionf#getEulerAnglesZYX(Vector3f)}
     * this was fixed in joml 1.10.6, but Minecraft ships with 1.10.5
     *
     * @param rot         Quaternion to get the euler angles for
     * @param eulerAngles Vector3f to store the angles in
     * @return
     */
    public static Vector3f getEulerAnglesZYX(Quaternionfc rot, Vector3f eulerAngles) {
        eulerAngles.x = org.joml.Math.atan2(rot.y() * rot.z() + rot.w() * rot.x(),
            0.5f - rot.x() * rot.x() - rot.y() * rot.y());
        eulerAngles.y = org.joml.Math.safeAsin(-2.0f * (rot.x() * rot.z() - rot.w() * rot.y()));
        eulerAngles.z = org.joml.Math.atan2(rot.x() * rot.y() + rot.w() * rot.z(),
            0.5f - rot.y() * rot.y() - rot.z() * rot.z());
        return eulerAngles;
    }

    /**
     * adds the give nvector to the translation component, without doing any Matrix multiplication
     *
     * @param matrix      Matrix to alter
     * @param translation Translation to add
     * @return the supplied matrix, for chaining
     */
    public static Matrix4f addTranslation(Matrix4f matrix, Vector3f translation) {
        return matrix.m30(matrix.m30() + translation.x)
            .m31(matrix.m31() + translation.y)
            .m32(matrix.m32() + translation.z);
    }

    /**
     * calculates the body yaw based on the two controller positions and the head direction
     *
     * @param rightHand right controller position
     * @param leftHand  left controller position
     * @param headDir   head direction
     * @return ywa in radians
     */
    public static float bodyYawRad(Vector3fc rightHand, Vector3fc leftHand, Vector3fc headDir) {
        // use an average of controller forward and head dir

        // use this when the hands are in front of the head
        Vector3f dir = leftHand.add(rightHand, new Vector3f());

        float hDot = MathUtils.normalizedDotXZ(dir, headDir);

        // BEHIND HEAD
        // use this when the hands are behind of the head
        // assuming the left controller is on the left side of the body, and the right one on the right side
        Vector3f armsForward = leftHand.sub(rightHand, new Vector3f()).rotateY(-Mth.HALF_PI);

        // TODO FBT this causes the body to flip when having the hands opposite each other, and looking 90° to the side
        // if hands are crossed, flip them
        if (armsForward.dot(headDir) < 0.0F) {
            armsForward.mul(-1.0F);
        }
        // BEHIND HEAD END

        // mix them based on how far they are to the side, to avoid jumping
        armsForward.lerp(dir, Math.max(0F, hDot), dir);

        // average with the head direction
        dir.normalize().lerp(headDir, 0.5F, dir);
        return (float) Math.atan2(-dir.x, dir.z);
    }

    /**
     * Converts a {@link Vector3fc} to a {@link Vec3}.
     * @param vec The original Vector3fc.
     * @return The Vec3.
     */
    public static Vec3 fromVector3fc(Vector3fc vec) {
        return new Vec3(vec.x(), vec.y(), vec.z());
    }
}
