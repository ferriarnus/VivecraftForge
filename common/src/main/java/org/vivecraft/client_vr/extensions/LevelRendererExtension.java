package org.vivecraft.client_vr.extensions;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.world.entity.Entity;

public interface LevelRendererExtension {
    /**
     * @return which entity is currently being rendered, {@code null} if there is none
     */
    Entity vivecraft$getRenderedEntity();

    /**
     * @return RenderTarget used for occluded stuff when using fabulous graphics
     */
    RenderTarget vivecraft$getAlphaSortVROccludedFramebuffer();

    /**
     * @return RenderTarget used for unoccluded stuff when using fabulous graphics
     */
    RenderTarget vivecraft$getAlphaSortVRUnoccludedFramebuffer();

    /**
     * @return RenderTarget used for the hands when using fabulous graphics
     */
    RenderTarget vivecraft$getAlphaSortVRHandsFramebuffer();
}
