package org.vivecraft.common.utils.math;

import org.joml.Vector3f;
import org.joml.Vector3fc;

public enum Axis {
    PITCH(1.0F, 0.0F, 0.0F),
    YAW(0.0F, 1.0F, 0.0F),
    ROLL(0.0F, 0.0F, 1.0F);

    private final Vector3fc vector;

    Axis(float x, float y, float z) {
        this.vector = new Vector3f(x, y, z);
    }

    public Vector3fc getVector() {
        return this.vector;
    }
}
