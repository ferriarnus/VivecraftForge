package org.vivecraft.common.utils.math;

import org.joml.Quaternionf;

public enum AngleOrder implements AngleOrderOperation {
    XYZ ((x, y, z) -> new Quaternionf().rotationXYZ(x, y, z)),
    ZYX ((x, y, z) -> new Quaternionf().rotationZYX(z, y, x)),
    YXZ ((x, y, z) -> new Quaternionf().rotationYXZ(y, x, z)),
    ZXY ((x, y, z) -> new Quaternionf().rotationZ(z).rotateX(x).rotateY(y)),
    YZX ((x, y, z) -> new Quaternionf().rotationY(y).rotateZ(z).rotateX(x)),
    XZY ((x, y, z) -> new Quaternionf().rotationX(x).rotateZ(z).rotateY(y));

    private final AngleOrderOperation angleOrderOperation;

    AngleOrder(final AngleOrderOperation angleOrderOperation) {
        this.angleOrderOperation = angleOrderOperation;
    }

    @Override
    public Quaternionf getRotation(float x, float y, float z) {
        return this.angleOrderOperation.getRotation(x, y, z);
    }
}
