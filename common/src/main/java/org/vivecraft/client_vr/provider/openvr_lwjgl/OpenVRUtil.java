package org.vivecraft.client_vr.provider.openvr_lwjgl;

import org.joml.Matrix4f;
import org.lwjgl.openvr.HmdMatrix34;
import org.lwjgl.openvr.HmdMatrix44;

public class OpenVRUtil {
    public static Matrix4f convertSteamVRMatrix3ToMatrix4f(HmdMatrix34 hmdMatrix, Matrix4f mat) {
        return mat.set(
            hmdMatrix.m(0), hmdMatrix.m(4), hmdMatrix.m(8), 0.0F,
            hmdMatrix.m(1), hmdMatrix.m(5), hmdMatrix.m(9), 0.0F,
            hmdMatrix.m(2), hmdMatrix.m(6), hmdMatrix.m(10), 0.0F,
            hmdMatrix.m(3), hmdMatrix.m(7), hmdMatrix.m(11), 1.0F);
    }

    public static Matrix4f Matrix4fFromOpenVR(HmdMatrix44 in) {
        return new Matrix4f(
            in.m(0), in.m(4), in.m(8), in.m(12),
            in.m(1), in.m(5), in.m(9), in.m(13),
            in.m(2), in.m(6), in.m(10), in.m(14),
            in.m(3), in.m(7), in.m(11), in.m(15));
    }
}
