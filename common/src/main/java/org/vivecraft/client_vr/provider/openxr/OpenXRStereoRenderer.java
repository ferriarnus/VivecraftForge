package org.vivecraft.client_vr.provider.openxr;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.util.Tuple;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL31;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRTextureTarget;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.render.RenderConfigException;
import org.vivecraft.client_vr.render.helpers.RenderHelper;

import java.io.IOException;
import java.nio.IntBuffer;

public class OpenXRStereoRenderer extends VRRenderer {
    private final MCOpenXR openxr;
    private int swapIndex;
    private VRTextureTarget[] leftFramebuffers;
    private VRTextureTarget[] rightFramebuffers;
    private boolean render;
    private XrCompositionLayerProjectionView.Buffer projectionLayerViews;
    private VRTextureTarget rightFramebuffer;
    private VRTextureTarget leftFramebuffer;


    public OpenXRStereoRenderer(MCOpenXR vr) {
        super(vr);
        this.openxr = vr;
    }

    @Override
    public void createRenderTexture(int width, int height) throws RenderConfigException{
        try (MemoryStack stack = MemoryStack.stackPush()) {

            //Get amount of views in the swapchain
            IntBuffer intBuffer = stack.ints(0); //Set value to 0
            int error = XR10.xrEnumerateSwapchainImages(openxr.swapchain, intBuffer, null);
            this.openxr.logError(error, "xrEnumerateSwapchainImages", "get count");

            //Now we know the amount, create the image buffer
            int imageCount = intBuffer.get(0);
            XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = this.openxr.device.createImageBuffers(imageCount, stack);

            error = XR10.xrEnumerateSwapchainImages(openxr.swapchain, intBuffer, XrSwapchainImageBaseHeader.create(swapchainImageBuffer.address(), swapchainImageBuffer.capacity()));
            this.openxr.logError(error, "xrEnumerateSwapchainImages", "get images");

            this.leftFramebuffers = new VRTextureTarget[imageCount];
            this.rightFramebuffers = new VRTextureTarget[imageCount];

            for (int i = 0; i < imageCount; i++) {
                XrSwapchainImageOpenGLKHR openxrImage = swapchainImageBuffer.get(i);
                leftFramebuffers[i] = new VRTextureTarget("L Eye " + i, width, height, openxrImage.image(), 0);
                RenderHelper.checkGLError("Left Eye framebuffer setup");
                rightFramebuffers[i] = new VRTextureTarget("R Eye " + i, width, height, openxrImage.image(), 1);
                RenderHelper.checkGLError("Right Eye framebuffer setup");
            }

            this.rightFramebuffer = new VRTextureTarget("R Eye mirror", width, height, true, -1, true, true, ClientDataHolderVR.getInstance().vrSettings.vrUseStencil);
            this.leftFramebuffer = new VRTextureTarget("L Eye mirror", width, height, true, -1, true, true, ClientDataHolderVR.getInstance().vrSettings.vrUseStencil);
        }
    }

    @Override
    public void setupRenderConfiguration(boolean render) throws IOException, RenderConfigException {
        super.setupRenderConfiguration(render);
        
        if (!render) {
            return;
        }
        this.projectionLayerViews = XrCompositionLayerProjectionView.calloc(2);
        try (MemoryStack stack = MemoryStack.stackPush()){

            IntBuffer intBuf2 = stack.callocInt(1);

            int error = XR10.xrAcquireSwapchainImage(
                openxr.swapchain,
                XrSwapchainImageAcquireInfo.calloc(stack).type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO),
                intBuf2);
            this.openxr.logError(error, "xrAcquireSwapchainImage", "");

            error = XR10.xrWaitSwapchainImage(openxr.swapchain,
                XrSwapchainImageWaitInfo.calloc(stack)
                    .type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO)
                    .timeout(XR10.XR_INFINITE_DURATION));
            this.openxr.logError(error, "xrWaitSwapchainImage", "");

            this.swapIndex = intBuf2.get(0);

            // Render view to the appropriate part of the swapchain image.
            for (int viewIndex = 0; viewIndex < 2; viewIndex++) {

                var subImage = projectionLayerViews.get(viewIndex)
                    .type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW)
                    .pose(openxr.viewBuffer.get(viewIndex).pose())
                    .fov(openxr.viewBuffer.get(viewIndex).fov())
                    .subImage();
                subImage.swapchain(openxr.swapchain);
                subImage.imageRect().offset().set(0, 0);
                subImage.imageRect().extent().set(openxr.width, openxr.height);
                subImage.imageArrayIndex(viewIndex);

            }
        }
    }

    @Override
    public Matrix4f getProjectionMatrix(int eyeType, float nearClip, float farClip) {
        XrFovf fov = openxr.viewBuffer.get(eyeType).fov();
        return new Matrix4f().setPerspectiveOffCenterFov(fov.angleLeft(), fov.angleRight(), fov.angleDown(), fov.angleUp(), nearClip, farClip);
    }

    @Override
    public void endFrame() throws RenderConfigException {

        GL31.glBindFramebuffer(GL31.GL_READ_FRAMEBUFFER, getLeftEyeTarget().frameBufferId);
        GL31.glBindFramebuffer(GL31.GL_DRAW_FRAMEBUFFER, leftFramebuffers[swapIndex].frameBufferId);
        GL31.glBlitFramebuffer(0,0, getLeftEyeTarget().viewWidth, getLeftEyeTarget().viewHeight, 0,0, leftFramebuffers[swapIndex].viewWidth, leftFramebuffers[swapIndex].viewHeight, GL31.GL_STENCIL_BUFFER_BIT | GL31.GL_COLOR_BUFFER_BIT, GL31.GL_NEAREST);

        GL31.glBindFramebuffer(GL31.GL_READ_FRAMEBUFFER, getRightEyeTarget().frameBufferId);
        GL31.glBindFramebuffer(GL31.GL_DRAW_FRAMEBUFFER, rightFramebuffers[swapIndex].frameBufferId);
        GL31.glBlitFramebuffer(0,0, getRightEyeTarget().viewWidth, getRightEyeTarget().viewHeight, 0,0, rightFramebuffers[swapIndex].viewWidth, rightFramebuffers[swapIndex].viewHeight, GL31.GL_STENCIL_BUFFER_BIT | GL31.GL_COLOR_BUFFER_BIT, GL31.GL_NEAREST);


        try (MemoryStack stack = MemoryStack.stackPush()){
            PointerBuffer layers = stack.callocPointer(1);
            int error;

            error = XR10.xrReleaseSwapchainImage(
                openxr.swapchain,
                XrSwapchainImageReleaseInfo.calloc(stack)
                    .type(XR10.XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO));
            this.openxr.logError(error, "xrReleaseSwapchainImage", "");

            XrCompositionLayerProjection compositionLayerProjection = XrCompositionLayerProjection.calloc(stack)
                .type(XR10.XR_TYPE_COMPOSITION_LAYER_PROJECTION)
                .space(openxr.xrAppSpace)
                .views(projectionLayerViews);

            layers.put(compositionLayerProjection);

            layers.flip();

            error = XR10.xrEndFrame(
                openxr.session,
                XrFrameEndInfo.calloc(stack)
                    .type(XR10.XR_TYPE_FRAME_END_INFO)
                    .displayTime(openxr.time)
                    .environmentBlendMode(XR10.XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
                    .layers(layers));
            this.openxr.logError(error, "xrEndFrame", "");

            projectionLayerViews.close();
        }
    }

    @Override
    public boolean providesStencilMask() {
        return false;
    }

    @Override
    public RenderTarget getLeftEyeTarget() {
        return leftFramebuffer;
    }

    @Override
    public RenderTarget getRightEyeTarget() {
        return rightFramebuffer;
    }

    @Override
    public String getName() {
        return "OpenXR";
    }

    @Override
    public Tuple<Integer, Integer> getRenderTextureSizes() {
        return new Tuple<>(openxr.width, openxr.height);
    }
}
