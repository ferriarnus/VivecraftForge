package org.vivecraft.client_xr.render_pass;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.vivecraft.client_vr.render.RenderPass;

import java.io.IOException;

public class WorldRenderPass implements AutoCloseable {

    public static WorldRenderPass STEREO_XR;
    public static WorldRenderPass CENTER;
    public static WorldRenderPass MIXED_REALITY;
    public static WorldRenderPass LEFT_TELESCOPE;
    public static WorldRenderPass RIGHT_TELESCOPE;
    public static WorldRenderPass CAMERA;

    public final RenderTarget target;

    /**
     * creates a WorldRenderPass that writes to {@code target}
     * @param target RenderTarget for this pass
     * @throws IOException when an error occurs during shader loading
     */
    public WorldRenderPass(RenderTarget target) throws IOException {
        this.target = target;
    }

    /**
     * @param pass RenderPass to get the WorldRenderPass for
     * @return the WorldRenderPass object corresponding to the given {@code pass}
     */
    public static WorldRenderPass getByRenderPass(RenderPass pass) {
        return switch (pass) {
            case CENTER -> CENTER;
            case THIRD -> MIXED_REALITY;
            case SCOPEL -> LEFT_TELESCOPE;
            case SCOPER -> RIGHT_TELESCOPE;
            case CAMERA -> CENTER;
            default -> STEREO_XR;
        };
    }

    /**
     * resizes the RenderTarget of this pass to the given size
     * @param width new width
     * @param height new height
     */
    public void resize(int width, int height) {
        this.target.resize(width, height, Minecraft.ON_OSX);
    }

    /**
     * releases all buffers hold by this pass
     */
    @Override
    public void close() {
        this.target.destroyBuffers();
    }
}
