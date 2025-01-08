package org.vivecraft.util;

public class VLoader {
    static {
        System.loadLibrary("vloader");
    }

    public static native long getEGLContext();
    public static native long getEGLConfig();
    public static native long getEGLDisplay();
    public static native long getDalvikVM();
    public static native long getDalvikActivity();
    public static native void setupAndroid();
}
