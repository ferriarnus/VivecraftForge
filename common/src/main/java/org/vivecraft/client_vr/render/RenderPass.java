package org.vivecraft.client_vr.render;

import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;

public enum RenderPass {
    LEFT,
    RIGHT,
    CENTER,
    THIRD,
    GUI,
    SCOPER,
    SCOPEL,
    CAMERA;

    public static boolean isFirstPerson(RenderPass pass) {
        return pass == LEFT || pass == RIGHT || pass == CENTER;
    }

    public static boolean isThirdPerson(RenderPass pass) {
        return pass == THIRD || pass == CAMERA;
    }

    public static boolean renderPlayer(RenderPass pass) {
        return pass == CAMERA ||
            (isFirstPerson(pass) && ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf) ||
            (pass == THIRD &&
            ClientDataHolderVR.getInstance().vrSettings.displayMirrorMode == VRSettings.MirrorMode.THIRD_PERSON
        );
    }

}
