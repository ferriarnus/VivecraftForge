package org.vivecraft.client_vr.utils.external;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import org.vivecraft.client_vr.settings.VRSettings;

public class jkatvr implements Library {
    public static final String KATVR_LIBRARY_NAME = "WalkerBase.dll";
    public static final NativeLibrary KATVR_NATIVE_LIB = NativeLibrary.getInstance(KATVR_LIBRARY_NAME);

    private static final float MAG = 0.15F;
    private static final float B_MAG = 0.1F;
    private static final float MAX_POWER = 3000.0F;

    private static float YAW;
    private static float YAW_OFFSET;
    private static double POWER;
    private static int DIRECTION;
    private static int IS_MOVING;
    private static IntByReference Y = new IntByReference();
    private static IntByReference M = new IntByReference();
    private static IntByReference IS = new IntByReference();
    private static DoubleByReference POW = new DoubleByReference();
    private static FloatByReference FL = new FloatByReference();

    public static native void Init(int var0);

    public static native int Launch();

    public static native boolean CheckForLaunch();

    public static native void Halt();

    public static native boolean GetWalkerData(int var0, IntByReference var1, DoubleByReference var2, IntByReference var3, IntByReference var4, FloatByReference var5);

    public static void query() {
        try {
            boolean flag = GetWalkerData(0, Y, POW, M, IS, FL);
            YAW = Y.getValue();
            POWER = POW.getValue();
            DIRECTION = -M.getValue();
            IS_MOVING = IS.getValue();
            YAW = YAW / 1024.0F * 360.0F;
        } catch (Exception exception) {
            VRSettings.LOGGER.error("Vivecraft: KATVR Error:", exception);
        }
    }

    public static float getYaw() {
        return YAW - YAW_OFFSET;
    }

    public static boolean isMoving() {
        return IS_MOVING == 1;
    }

    public static void resetYaw(float offsetDegrees) {
        YAW_OFFSET = offsetDegrees + YAW;
    }

    public static float walkDirection() {
        return (float) DIRECTION;
    }

    public static float getSpeed() {
        return (float) (POWER / MAX_POWER * (walkDirection() == 1.0F ? MAG : B_MAG));
    }

    static {
        Native.register(jkatvr.class, KATVR_NATIVE_LIB);
    }
}
