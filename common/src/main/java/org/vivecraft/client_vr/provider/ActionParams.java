package org.vivecraft.client_vr.provider;

import org.vivecraft.client_vr.provider.control.VRInputActionSet;

/**
 * holds the parameters for a VR action key
 *
 * @param requirement       if the action is "optional", "suggested" or "mandatory"
 * @param type              input type of the action. one of "boolean", "vector1, "vector2" or "vector3"
 * @param actionSetOverride action set to put it in, any of {@link VRInputActionSet}
 */
public record ActionParams(String requirement, String type, VRInputActionSet actionSetOverride) {
    public static final ActionParams DEFAULT = new ActionParams("optional", "boolean", null);
}
