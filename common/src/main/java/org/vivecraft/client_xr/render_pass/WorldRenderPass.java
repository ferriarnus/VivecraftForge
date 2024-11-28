package org.vivecraft.client_xr.render_pass;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

public class WorldRenderPass implements AutoCloseable {

    private static final Minecraft MC = Minecraft.getInstance();

    public static WorldRenderPass STEREO_XR;
    public static WorldRenderPass CENTER;
    public static WorldRenderPass MIXED_REALITY;
    public static WorldRenderPass LEFT_TELESCOPE;
    public static WorldRenderPass RIGHT_TELESCOPE;
    public static WorldRenderPass CAMERA;


    public final RenderTarget target;
    public final PostChain transparencyChain;
    public final PostChain outlineChain;
    public PostChain postEffect = null;

    /**
     * creates a WorldRenderPass that writes to {@code target}
     * @param target RenderTarget for this pass
     * @throws IOException when an error occurs during shader loading
     */
    public WorldRenderPass(RenderTarget target) throws IOException {
        this.target = target;
        if (Minecraft.useShaderTransparency()) {
            this.transparencyChain = createPostChain(new ResourceLocation("shaders/post/vrtransparency.json"), this.target);
        } else {
            this.transparencyChain = null;
        }
        this.outlineChain = createPostChain(new ResourceLocation("shaders/post/entity_outline.json"), this.target);
    }

    /**
     * creates a post chain for the given shader, and resizes it to the {@code target} size
     * @param resourceLocation shader to load
     * @param target RenderTarget the shader should write to
     * @throws IOException when an error occurs during shader loading
     */
    public static PostChain createPostChain(ResourceLocation resourceLocation, RenderTarget target) throws IOException {
        PostChain postchain = new PostChain(MC.getTextureManager(), MC.getResourceManager(), target, resourceLocation);
        postchain.resize(target.viewWidth, target.viewHeight);
        return postchain;
    }

    /**
     * resizes the RenderTarget and the PostChains of this pass to the given size
     * @param width new width
     * @param height new height
     */
    public void resize(int width, int height) {
        this.target.resize(width, height, Minecraft.ON_OSX);
        this.outlineChain.resize(width, height);
        if (this.transparencyChain != null) {
            this.transparencyChain.resize(width, height);
        }
        if (this.postEffect != null) {
            this.postEffect.resize(width, height);
        }
    }

    /**
     * releases all buffers hold by this pass
     */
    @Override
    public void close() {
        this.target.destroyBuffers();
        if (this.transparencyChain != null) {
            this.transparencyChain.close();
        }
        this.outlineChain.close();
        if (this.postEffect != null) {
            this.postEffect.close();
            this.postEffect = null;
        }
    }
}
