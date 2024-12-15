package org.vivecraft.client_vr.utils.osc_trackers;

import org.joml.Matrix4f;

public class OSCTracker {
    private final static long TIMEOUT = 10000L;

    public final Matrix4f pose = new Matrix4f();
    protected long timeStamp;

    public boolean isTracking() {
        return System.currentTimeMillis() - this.timeStamp < TIMEOUT;
    }
}
