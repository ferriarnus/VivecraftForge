package org.vivecraft.client_vr.provider.nullvr;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public enum ControllerTransform {
    NULL,
    VIVE(
        new Vector3f(0.0F, 0.003F, 0.097F),
        new Vector3f(5.037F,0.0F,0.0F),
        new Vector3f(0.0F, -0.01F, -0.007F),
        new Vector3f(1.282F,0.0F,0.0F)),
    RIFT_QUEST1(
        new Vector3f(0.0F, 0.003F, 0.097F),
        new Vector3f(5.037F,0.0F,0.0F),
        new Vector3f(-0.00629F,-0.02522F,0.03469F),
        new Vector3f(-39.4F,0.0F,0.0F)),
    QUEST2_PRO_PLUS(
        new Vector3f(0.0F, 0.003F, 0.097F),
        new Vector3f(5.037F,0.0F,0.0F),
        new Vector3f(-0.016694F, -0.02522F ,0.024687F),
        new Vector3f(-37.4F,0.0F,0.0F)),
    INDEX(
        new Vector3f(0.003851F, 0.003715F, 0.075948F),
        new Vector3f(15.392F, 2.071F, -0.303F),
        new Vector3f(-0.006F, -0.015F, 0.02F),
        new Vector3f(-40.0F, 5.0F, 0.0F)),
    PICO4(
        new Vector3f(0.0F, 0.003F, 0.097F),
        new Vector3f(5.037F, 0.0F, 0.0F),
        new Vector3f(-0.012F, -0.01F, -0.007F),
        new Vector3f(-1.282F, 0.0F, 0.0F));


    public final Matrix4f handGripR;
    public final Matrix4f tipR;
    public final Matrix4f handGripL;
    public final Matrix4f tipL;

    ControllerTransform() {
        this.handGripR = this.handGripL = this.tipR = this.tipL = new Matrix4f();
    }

    ControllerTransform(Vector3f handGripOrigin, Vector3f handGripRot, Vector3f tipOrigin, Vector3f tipRot) {
        this.handGripR = new Matrix4f()
            .translation(handGripOrigin)
            .rotateXYZ(handGripRot.mul(Mth.DEG_TO_RAD, new Vector3f()));
        this.handGripL = new Matrix4f()
            .translation(handGripOrigin.mul(-1, 1, 1))
            .rotateXYZ(handGripRot.mul(Mth.DEG_TO_RAD, new Vector3f()).mul(1, -1, -1));
        this.tipR = new Matrix4f()
            .translation(tipOrigin)
            .rotateXYZ(tipRot.mul(Mth.DEG_TO_RAD, new Vector3f()));
        this.tipL = new Matrix4f()
            .translation(tipOrigin.mul(-1, 1, 1))
            .rotateXYZ(tipRot.mul(Mth.DEG_TO_RAD, new Vector3f()).mul(1, -1, -1));
    }
}
