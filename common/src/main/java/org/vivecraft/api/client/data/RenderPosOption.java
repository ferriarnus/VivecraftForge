package org.vivecraft.api.client.data;

import com.google.common.annotations.Beta;
import net.minecraft.world.InteractionHand;

/**
 * The option to pass to {@link org.vivecraft.api.client.VRRenderingAPI#getRenderPos(RenderPosOption)}.
 */
@Beta
public enum RenderPosOption {
    MAIN_HAND, OFF_HAND, THIRD_PERSON_CAMERA
}
