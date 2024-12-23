package org.vivecraft.client_vr.provider.openxr;

import com.mojang.blaze3d.platform.Window;
import com.sun.jna.Platform;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWNativeGLX;
import org.lwjgl.glfw.GLFWNativeWGL;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.windows.User32;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.util.VLoader;

import java.util.Objects;

import static org.lwjgl.opengl.GLX13.*;
import static org.lwjgl.system.MemoryStack.stackInts;
import static org.lwjgl.system.MemoryUtil.NULL;

//TODO: VulkanMod Support
public interface DeviceCompat {
    long getPlatformInfo(MemoryStack stack);
    void initOpenXRLoader(MemoryStack stack);
    String getGraphicsExtension();
    XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack);
    Struct checkGraphics(MemoryStack stack, XrInstance instance, long systemID);
    static DeviceCompat detectDevice() {
        return System.getProperty("os.version").contains("Android") ? new Mobile() : new Desktop();
    }

    class Desktop implements DeviceCompat {
        @Override
        public long getPlatformInfo(MemoryStack stack) {
            return NULL;
        }

        @Override
        public void initOpenXRLoader(MemoryStack stack) {
            VRSettings.LOGGER.info("Platform: {}", System.getProperty("os.version"));
        }

        @Override
        public String getGraphicsExtension() {
            return KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME;
        }

        @Override
        public XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack) {
            XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = XrSwapchainImageOpenGLKHR.calloc(imageCount, stack);
            for (XrSwapchainImageOpenGLKHR image : swapchainImageBuffer) {
                image.type(KHROpenGLEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR);
            }

            return swapchainImageBuffer;
        }

        @Override
        public Struct checkGraphics(MemoryStack stack, XrInstance instance, long systemID) {
            XrGraphicsRequirementsOpenGLKHR graphicsRequirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack).type(KHROpenGLEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);
            KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(instance, systemID, graphicsRequirements);
            //Bind the OpenGL context to the OpenXR instance and create the session
            Window window = Minecraft.getInstance().getWindow();
            long windowHandle = window.getWindow();
            if (Platform.getOSType() == Platform.WINDOWS) {
                return XrGraphicsBindingOpenGLWin32KHR.calloc(stack).set(
                    KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_WIN32_KHR,
                    NULL,
                    User32.GetDC(GLFWNativeWin32.glfwGetWin32Window(windowHandle)),
                    GLFWNativeWGL.glfwGetWGLContext(windowHandle)
                );
            } else if (Platform.getOSType() == Platform.LINUX) {
                long xDisplay = GLFWNativeX11.glfwGetX11Display();

                long glXContext = GLFWNativeGLX.glfwGetGLXContext(windowHandle);
                long glXWindowHandle = GLFWNativeGLX.glfwGetGLXWindow(windowHandle);

                int fbXID = glXQueryDrawable(xDisplay, glXWindowHandle, GLX_FBCONFIG_ID);
                PointerBuffer fbConfigBuf = glXChooseFBConfig(xDisplay, X11.XDefaultScreen(xDisplay), stackInts(GLX_FBCONFIG_ID, fbXID, 0));
                if (fbConfigBuf == null) {
                    throw new IllegalStateException("Your framebuffer config was null, make a github issue");
                }
                long fbConfig = fbConfigBuf.get();

                return XrGraphicsBindingOpenGLXlibKHR.calloc(stack).set(
                    KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_XLIB_KHR,
                    NULL,
                    xDisplay,
                    (int) Objects.requireNonNull(glXGetVisualFromFBConfig(xDisplay, fbConfig)).visualid(),
                    fbConfig,
                    glXWindowHandle,
                    glXContext
                );
            } else {
                throw new IllegalStateException("Macos not supported");
            }
        }
    }

    class Mobile implements DeviceCompat {
        @Override
        public long getPlatformInfo(MemoryStack stack) {
            return XrInstanceCreateInfoAndroidKHR.calloc(stack).set(
                KHRAndroidCreateInstance.XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR,
                NULL,
                VLoader.getDalvikVM(),
                VLoader.getDalvikActivity()
            ).address();
        }

        @Override
        public void initOpenXRLoader(MemoryStack stack) {
            VRSettings.LOGGER.info("Platform: {}", System.getProperty("os.version"));
            VLoader.setupAndroid();
            XrLoaderInitInfoAndroidKHR initInfo = XrLoaderInitInfoAndroidKHR.calloc(stack).set(
                KHRLoaderInitAndroid.XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR,
                NULL,
                VLoader.getDalvikVM(),
                VLoader.getDalvikActivity()
            );

            KHRLoaderInit.xrInitializeLoaderKHR(XrLoaderInitInfoBaseHeaderKHR.create(initInfo.address()));
        }

        @Override
        public String getGraphicsExtension() {
            return KHROpenGLESEnable.XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME;
        }

        @Override
        public XrSwapchainImageOpenGLKHR.Buffer createImageBuffers(int imageCount, MemoryStack stack) {
            XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = XrSwapchainImageOpenGLKHR.calloc(imageCount, stack);
            for (XrSwapchainImageOpenGLKHR image : swapchainImageBuffer) {
                image.type(KHROpenGLESEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR);
            }

            return swapchainImageBuffer;
        }

        @Override
        public Struct checkGraphics(MemoryStack stack, XrInstance instance, long systemID) {
            XrGraphicsRequirementsOpenGLESKHR graphicsRequirements = XrGraphicsRequirementsOpenGLESKHR.calloc(stack).type(KHROpenGLESEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR);
            KHROpenGLESEnable.xrGetOpenGLESGraphicsRequirementsKHR(instance, systemID, graphicsRequirements);
            XrGraphicsBindingOpenGLESAndroidKHR graphicsBinding = XrGraphicsBindingOpenGLESAndroidKHR.calloc(stack);
            graphicsBinding.set(
                KHROpenGLESEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR,
                NULL,
                VLoader.getEGLDisplay(),
                VLoader.getEGLConfig(),
                VLoader.getEGLContext()
            );

            return graphicsBinding;
        }
    }
}
