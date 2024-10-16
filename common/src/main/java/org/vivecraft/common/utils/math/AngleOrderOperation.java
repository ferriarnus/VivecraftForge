package org.vivecraft.common.utils.math;

import org.joml.Quaternionf;

@FunctionalInterface
public interface AngleOrderOperation {
    Quaternionf getRotation(float x, float y, float z);
}
