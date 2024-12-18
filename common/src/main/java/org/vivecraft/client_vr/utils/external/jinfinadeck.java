package org.vivecraft.client_vr.utils.external;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.ptr.IntByReference;
import net.minecraft.util.Mth;
import org.vivecraft.client_vr.settings.VRSettings;

public class jinfinadeck implements Library {
    public static final String INFINADECK_LIBRARY_NAME = "InfinadeckAPI.dll";
    public static final NativeLibrary INFINADECK_NATIVE_LIB = NativeLibrary.getInstance(INFINADECK_LIBRARY_NAME);
    private static final float MAG = 0.15F;
    private static final float B_MAG = 0.1F;
    private static final float MAX_POWER = 2.0F;

    private static float YAW;
    private static float YAW_OFFSET;
    private static double POWER;
    private static int DIRECTION;
    private static boolean IS_MOVING;

    public static native int InitInternal(IntByReference var0, boolean var1);

    public static native int DeInitInternal();

    public static native boolean CheckConnection();

    public static native boolean GetTreadmillRunState();

    public static native double GetFloorSpeedAngle();

    public static native double GetFloorSpeedMagnitude();

    public static boolean InitConnection() {
        IntByReference intbyreference = new IntByReference();
        InitInternal(intbyreference, false);

        if (intbyreference.getValue() != 0) {
            InitInternal(intbyreference, true);
        }

        return intbyreference.getValue() == 0;
    }

    public static void Destroy() {
        DeInitInternal();
    }

    public static void query() {
        try {
            if (CheckConnection()) {
            }

            YAW = (float) GetFloorSpeedAngle();
            POWER = GetFloorSpeedMagnitude();
            DIRECTION = 1;
            IS_MOVING = GetTreadmillRunState();
            YAW *= Mth.RAD_TO_DEG;
        } catch (Exception exception) {
            VRSettings.LOGGER.error("Vivecraft: Infinadeck Error:", exception);
        }
    }

    public static float getYaw() {
        return YAW - YAW_OFFSET;
    }

    public static boolean isMoving() {
        return true;
    }

    public static void resetYaw(float offsetDegrees) {
        YAW_OFFSET = offsetDegrees + YAW;
    }

    public static float walkDirection() {
        return DIRECTION;
    }

    public static float getSpeed() {
        return (float) (POWER / MAX_POWER * (walkDirection() == 1.0F ? MAG : B_MAG));
    }

    static {
        Native.register(jinfinadeck.class, INFINADECK_NATIVE_LIB);
    }
}
