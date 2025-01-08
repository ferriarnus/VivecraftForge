package org.vivecraft.client_vr.provider.openxr;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrQuaternionf;

public class OpenXRUtil {

    public static void openXRPoseToMarix(XrPosef pose, Matrix4f mat) {
        mat.set(new Quaternionf(pose.orientation().x(), pose.orientation().y(), pose.orientation().z(),
                pose.orientation().w()))
            .setTranslation(pose.position$().x(), pose.position$().y(), pose.position$().z())
            .m33(1);
    }

    public static void openXRPoseToMarix(XrQuaternionf quat, Matrix4f mat) {
        mat.set(new Quaternionf(quat.x(), quat.y(), quat.z(), quat.w()));
    }
}
