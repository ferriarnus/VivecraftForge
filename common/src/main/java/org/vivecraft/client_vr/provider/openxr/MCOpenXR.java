package org.vivecraft.client_vr.provider.openxr;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.VRRenderer;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;
import org.vivecraft.client_vr.render.RenderPass;
import org.vivecraft.client_vr.settings.VRSettings;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class MCOpenXR extends MCVR {

    private static MCOpenXR OME;
    public XrInstance instance;
    public XrSession session;
    public XrSpace xrAppSpace;
    public XrSpace xrViewSpace;
    public XrSwapchain swapchain;
    public final XrEventDataBuffer eventDataBuffer = XrEventDataBuffer.calloc();
    public long time;
    private boolean tried;
    private long systemID;
    public XrView.Buffer viewBuffer;
    public int width;
    public int height;
    //TODO either move to MCVR, Or make special for OpenXR holding the instance itself.
    private final Map<VRInputActionSet, Long> actionSetHandles = new EnumMap<>(VRInputActionSet.class);
    //TODO Move to MCVR
    private  XrActiveActionSet.Buffer activeActionSetsBuffer;
    private boolean isActive;
    private final HashMap<String, Long> paths = new HashMap<>();
    private final long[] grip = new long[2];
    private final long[] aim = new long[2];
    private final XrSpace[] gripSpace = new XrSpace[2];
    private final XrSpace[] aimSpace = new XrSpace[2];
    public static final XrPosef POSE_IDENTITY = XrPosef.calloc().set(
        XrQuaternionf.calloc().set(0, 0, 0, 1),
        XrVector3f.calloc()
    );
    public boolean shouldRender = true;
    public long[] haptics = new long[2];
    public String systemName;


    public MCOpenXR(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh, VivecraftVRMod.INSTANCE);
        OME = this;
        this.hapticScheduler = new OpenXRHapticSchedular();
    }

    @Override
    public String getName() {
        return "OpenXR";
    }

    @Override
    public void destroy() {
        int error;
        //Not sure if we need the action sets one here, as we are shutting down
        for (Long inputActionSet : actionSetHandles.values()){
            error = XR10.xrDestroyActionSet(new XrActionSet(inputActionSet, instance));
            logError(error, "xrDestroyActionSet", "");
        }
        if (swapchain != null) {
            error = XR10.xrDestroySwapchain(swapchain);
            logError(error, "xrDestroySwapchain", "");
        }
        if (viewBuffer != null) {
            viewBuffer.close();
        }
        if (xrAppSpace != null) {
            error = XR10.xrDestroySpace(xrAppSpace);
            logError(error, "xrDestroySpace", "xrAppSpace");
        }
        if (xrViewSpace != null) {
            error = XR10.xrDestroySpace(xrViewSpace);
            logError(error, "xrDestroySpace", "xrViewSpace");
        }
        if (session != null){
            error = XR10.xrDestroySession(session);
            logError(error, "xrDestroySession", "");
        }
        if (instance != null){
            error = XR10.xrDestroyInstance(instance);
            logError(error, "xrDestroyInstance", "");
        }
        eventDataBuffer.close();
    }

    @Override
    protected ControllerType findActiveBindingControllerType(KeyMapping binding) {
        if (!this.inputInitialized) {
            return null;
        } else {
            long path = this.getInputAction(binding).getLastOrigin();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer buf = stack.callocInt(1);
                int error = XR10.xrPathToString(instance, path, buf, null);
                logError(error, "xrPathToString", "get string length for", binding.getName());

                int size = buf.get();
                if (size <= 0) {
                    return null;
                }

                buf = stack.callocInt(size);
                ByteBuffer byteBuffer = stack.calloc(size);
                error = XR10.xrPathToString(instance, path, buf, byteBuffer);
                logError(error, "xrPathToString", "get string for", binding.getName());
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                String name = new String(bytes);
                if (name.contains("right")) {
                    return ControllerType.RIGHT;
                }
                return ControllerType.LEFT;
            }
        }
    }

    @Override
    public void poll(long var1) {
        if (this.initialized) {
            this.mc.getProfiler().push("events");
            this.pollVREvents();

            if (!this.dh.vrSettings.seated) {
                this.mc.getProfiler().popPush("controllers");
                this.mc.getProfiler().push("gui");

                if (this.mc.screen == null && this.dh.vrSettings.vrTouchHotbar) {

                    if (this.dh.vrSettings.vrHudLockMode != VRSettings.HUDLock.HEAD && this.hudPopup) {
                        this.processHotbar();
                    }
                }

                this.mc.getProfiler().pop();
            }
            this.mc.getProfiler().popPush("updatePose/Vsync");
            this.updatePose();
            this.mc.getProfiler().popPush("processInputs");
            this.processInputs();
            this.mc.getProfiler().popPush("hmdSampling");
            this.hmdSampling();
            this.mc.getProfiler().pop();
        }
    }

    private void updatePose() {
        if (mc == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrFrameState frameState = XrFrameState.calloc(stack).type(XR10.XR_TYPE_FRAME_STATE);

            int error = XR10.xrWaitFrame(
                session,
                XrFrameWaitInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_WAIT_INFO),
                frameState);
            logError(error, "xrWaitFrame", "");

            time = frameState.predictedDisplayTime();
            this.shouldRender = frameState.shouldRender();

            error = XR10.xrBeginFrame(
                session,
                XrFrameBeginInfo.calloc(stack).type(XR10.XR_TYPE_FRAME_BEGIN_INFO));
            logError(error, "xrBeginFrame", "");


            XrViewState viewState = XrViewState.calloc(stack).type(XR10.XR_TYPE_VIEW_STATE);
            IntBuffer intBuf = stack.callocInt(1);

            XrViewLocateInfo viewLocateInfo = XrViewLocateInfo.calloc(stack);
            viewLocateInfo.set(XR10.XR_TYPE_VIEW_LOCATE_INFO,
                0,
                XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                frameState.predictedDisplayTime(),
                xrAppSpace
            );

            error = XR10.xrLocateViews(session, viewLocateInfo, viewState, intBuf, viewBuffer);
            logError(error, "xrLocateViews", "");

            XrSpaceLocation space_location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);

            //HMD pose
            error = XR10.xrLocateSpace(xrViewSpace, xrAppSpace, time, space_location);
            logError(error, "xrLocateSpace", "xrViewSpace");
            if (error >= 0) {
                OpenXRUtil.openXRPoseToMarix(space_location.pose(), this.hmdPose);
                headIsTracking = true;
            } else {
                headIsTracking = false;
                this.hmdPose.identity();
                this.hmdPose.m31(1.6F);
            }

            //Eye positions
            OpenXRUtil.openXRPoseToMarix(viewBuffer.get(0).pose(), this.hmdPoseLeftEye);
            OpenXRUtil.openXRPoseToMarix(viewBuffer.get(1).pose(), this.hmdPoseRightEye);

            if (this.inputInitialized) {
                this.mc.getProfiler().push("updateActionState");

                if (this.updateActiveActionSets()) {
                    XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                        .type(XR10.XR_TYPE_ACTIONS_SYNC_INFO)
                        .activeActionSets(activeActionSetsBuffer);
                    error = XR10.xrSyncActions(session, syncInfo);
                    logError(error, "xrSyncActions", "");
                }

                this.inputActions.values().forEach(this::readNewData);

                //TODO Not needed it seems? Poses come from the action space
                XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
                this.readPoseData(this.grip[RIGHT_CONTROLLER], actionSet);
                this.readPoseData(this.grip[LEFT_CONTROLLER], actionSet);
                this.readPoseData(this.aim[RIGHT_CONTROLLER], actionSet);
                this.readPoseData(this.aim[LEFT_CONTROLLER], actionSet);

                this.mc.getProfiler().pop();

                //reverse
                if (this.dh.vrSettings.reverseHands) {
                    XrSpace temp = gripSpace[RIGHT_CONTROLLER];
                    gripSpace[RIGHT_CONTROLLER] = gripSpace[LEFT_CONTROLLER];
                    gripSpace[LEFT_CONTROLLER] = temp;
                    temp = aimSpace[RIGHT_CONTROLLER];
                    aimSpace[RIGHT_CONTROLLER] = aimSpace[LEFT_CONTROLLER];
                    aimSpace[LEFT_CONTROLLER] = temp;
                }

                //Controller aim and grip poses
                error = XR10.xrLocateSpace(gripSpace[RIGHT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "gripSpace[0]");
                if (error >= 0) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.handRotation[RIGHT_CONTROLLER]);
                }

                error = XR10.xrLocateSpace(gripSpace[LEFT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "gripSpace[1]");
                if (error >= 0) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.handRotation[LEFT_CONTROLLER]);
                }

                error = XR10.xrLocateSpace(aimSpace[RIGHT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "aimSpace[0]");
                if (error >= 0) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose(), this.controllerPose[RIGHT_CONTROLLER]);
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.controllerRotation[RIGHT_CONTROLLER]);
                    this.controllerTracking[RIGHT_CONTROLLER] = true;
                } else {
                    this.controllerTracking[RIGHT_CONTROLLER] = false;
                }

                error = XR10.xrLocateSpace(aimSpace[LEFT_CONTROLLER], xrAppSpace, time, space_location);
                logError(error, "xrLocateSpace", "aimSpace[1]");
                if (error >= 0) {
                    OpenXRUtil.openXRPoseToMarix(space_location.pose(), this.controllerPose[LEFT_CONTROLLER]);
                    OpenXRUtil.openXRPoseToMarix(space_location.pose().orientation(), this.controllerRotation[LEFT_CONTROLLER]);
                    this.controllerTracking[LEFT_CONTROLLER] = true;
                } else {
                    this.controllerTracking[LEFT_CONTROLLER] = false;
                }
            }

            this.updateAim();
        }
    }

    public void readNewData(VRInputAction action) {
        switch (action.type) {
            case "boolean" -> {
                if (action.isHanded()) {
                    for (ControllerType controllertype1 : ControllerType.values()) {
                        this.readBoolean(action, controllertype1);
                    }
                } else {
                    this.readBoolean(action, null);
                }
            }

            case "vector1" -> {
                if (action.isHanded()) {
                    for (ControllerType controllertype : ControllerType.values()) {
                        this.readFloat(action, controllertype);
                    }
                } else {
                    this.readFloat(action, null);
                }
            }

            case "vector2" -> {
                if (action.isHanded()) {
                    for (ControllerType controllertype : ControllerType.values()) {
                        this.readVecData(action, controllertype);
                    }
                } else {
                    this.readVecData(action, null);
                }
            }

        }
    }

    private void readBoolean(VRInputAction action, ControllerType hand) {
        int i = 0;

        if (hand != null) {
            i = hand.ordinal();
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action.handle, new XrActionSet(actionSetHandles.get(action.actionSet), instance)));
            XrActionStateBoolean state = XrActionStateBoolean.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_BOOLEAN);
            int error = XR10.xrGetActionStateBoolean(session, info, state);
            logError(error, "xrGetActionStateBoolean",  action.name);

            action.digitalData[i].state = state.currentState();
            action.digitalData[i].isActive = state.isActive();
            action.digitalData[i].isChanged = state.changedSinceLastSync();
            action.digitalData[i].activeOrigin = getOrigins(action).get(0);
        }
    }

    private void readFloat(VRInputAction action, ControllerType hand) {
        int i = 0;

        if (hand != null) {
            i = hand.ordinal();
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action.handle, new XrActionSet(actionSetHandles.get(action.actionSet), instance)));
            XrActionStateFloat state = XrActionStateFloat.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_FLOAT);
            int error = XR10.xrGetActionStateFloat(session, info, state);
            logError(error, "xrGetActionStateFloat",  action.name);

            action.analogData[i].deltaX = action.analogData[i].x - state.currentState();
            action.analogData[i].x = state.currentState();
            action.analogData[i].activeOrigin = getOrigins(action).get(0);
            action.analogData[i].isActive = state.isActive();
            action.analogData[i].isChanged = state.changedSinceLastSync();
        }
    }

    private void readVecData(VRInputAction action, ControllerType hand) {
        int i = 0;

        if (hand != null) {
            i = hand.ordinal();
        }
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action.handle, new XrActionSet(actionSetHandles.get(action.actionSet), instance)));
            XrActionStateVector2f state = XrActionStateVector2f.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_VECTOR2F);
            int error = XR10.xrGetActionStateVector2f(session, info, state);
            logError(error, "xrGetActionStateVector2f",  action.name);

            action.analogData[i].deltaX = action.analogData[i].x - state.currentState().x();
            action.analogData[i].deltaY = action.analogData[i].y - state.currentState().y();
            action.analogData[i].x = state.currentState().x();
            action.analogData[i].y = state.currentState().y();
            action.analogData[i].activeOrigin = getOrigins(action).get(0);
            action.analogData[i].isActive = state.isActive();
            action.analogData[i].isChanged = state.changedSinceLastSync();
        }
    }

    private void readPoseData(Long action, XrActionSet set) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionStateGetInfo info = XrActionStateGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_STATE_GET_INFO);
            info.action(new XrAction(action, set));
            XrActionStatePose state = XrActionStatePose.calloc(stack).type(XR10.XR_TYPE_ACTION_STATE_POSE);
            int error = XR10.xrGetActionStatePose(session, info, state);
            logError(error, "xrGetActionStatePose",  "");
        }
    }

    private boolean updateActiveActionSets() {
        ArrayList<VRInputActionSet> arraylist = new ArrayList<>();
        arraylist.add(VRInputActionSet.GLOBAL);

        // we are always modded
        arraylist.add(VRInputActionSet.MOD);

        arraylist.add(VRInputActionSet.MIXED_REALITY);
        arraylist.add(VRInputActionSet.TECHNICAL);

        if (this.mc.screen == null) {
            arraylist.add(VRInputActionSet.INGAME);
            arraylist.add(VRInputActionSet.CONTEXTUAL);
        } else {
            arraylist.add(VRInputActionSet.GUI);
            if (ClientDataHolderVR.getInstance().vrSettings.ingameBindingsInGui) {
                arraylist.add(VRInputActionSet.INGAME);
            }
        }

        if (KeyboardHandler.SHOWING || RadialHandler.isShowing()) {
            arraylist.add(VRInputActionSet.KEYBOARD);
        }

        if (this.activeActionSetsBuffer == null) {
            activeActionSetsBuffer = XrActiveActionSet.calloc(arraylist.size());
        } else if (activeActionSetsBuffer.capacity() != arraylist.size()) {
            activeActionSetsBuffer.close();
            activeActionSetsBuffer = XrActiveActionSet.calloc(arraylist.size());
        }

        for (int i = 0; i < arraylist.size(); ++i) {
            VRInputActionSet vrinputactionset = arraylist.get(i);
            activeActionSetsBuffer.get(i).set(new XrActionSet(this.getActionSetHandle(vrinputactionset), instance), NULL);
        }

        return !arraylist.isEmpty();
    }

    long getActionSetHandle(VRInputActionSet actionSet) {
        return this.actionSetHandles.get(actionSet);
    }

    private void pollVREvents() {
        while (true) {
            eventDataBuffer.clear();
            eventDataBuffer.type(XR10.XR_TYPE_EVENT_DATA_BUFFER);
            int error = XR10.xrPollEvent(instance, eventDataBuffer);
            logError(error, "xrPollEvent",  "");
            if (error != XR10.XR_SUCCESS) {
                break;
            }
            XrEventDataBaseHeader event = XrEventDataBaseHeader.create(eventDataBuffer.address());

            switch (event.type()) {
                case XR10.XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING -> {
                    XrEventDataInstanceLossPending instanceLossPending = XrEventDataInstanceLossPending.create(event.address());
                }
                case XR10.XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED -> {
                    this.sessionChanged(XrEventDataSessionStateChanged.create(event.address()));
                }
                case XR10.XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED -> {
                }
                case XR10.XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING -> {
                }
                default -> {
                }
            }
        }
    }

    private void sessionChanged(XrEventDataSessionStateChanged xrEventDataSessionStateChanged) {
        int state = xrEventDataSessionStateChanged.state();

        switch (state) {
            case XR10.XR_SESSION_STATE_READY: {
                try (MemoryStack stack = MemoryStack.stackPush()){
                    XrSessionBeginInfo sessionBeginInfo = XrSessionBeginInfo.calloc(stack);
                    sessionBeginInfo.type(XR10.XR_TYPE_SESSION_BEGIN_INFO);
                    sessionBeginInfo.next(NULL);
                    sessionBeginInfo.primaryViewConfigurationType(XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);

                    int error = XR10.xrBeginSession(session, sessionBeginInfo);
                    logError(error, "xrBeginSession",  "XR_SESSION_STATE_READY");
                }
                this.isActive = true;
                break;
            }
            case XR10.XR_SESSION_STATE_STOPPING: {
                this.isActive = false;
                int error = XR10.xrEndSession(session);
                logError(error, "xrEndSession",  "XR_SESSION_STATE_STOPPING");
            }
            case XR10.XR_SESSION_STATE_VISIBLE, XR10.XR_SESSION_STATE_FOCUSED: {
                this.isActive = true;
                break;
            }
            case XR10.XR_SESSION_STATE_EXITING, XR10.XR_SESSION_STATE_IDLE, XR10.XR_SESSION_STATE_SYNCHRONIZED: {
                this.isActive = false;
                break;
            }
            case XR10.XR_SESSION_STATE_LOSS_PENDING: {
                break;
            }
            default:
                break;
        }
    }

    @Override
    public Vector2f getPlayAreaSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrExtent2Df vec = XrExtent2Df.calloc(stack);
            int error = XR10.xrGetReferenceSpaceBoundsRect(session, XR10.XR_REFERENCE_SPACE_TYPE_STAGE, vec);
            logError(error, "xrGetReferenceSpaceBoundsRect",  "");
            return new Vector2f(vec.width(), vec.height());
        }
    }

    @Override
    public boolean init() {
        if (this.initialized) {
            return true;
        } else if (this.tried) {
            return this.initialized;
        } else {
            tried = true;
            this.mc = Minecraft.getInstance();
            try {
                this.initializeOpenXRInstance();
                this.initializeOpenXRSession();
                this.initializeOpenXRSpace();
                this.initializeOpenXRSwapChain();
                this.initInputAndApplication();
            } catch (Exception e) {
                e.printStackTrace();
                this.initSuccess = false;
                this.initStatus = e.getLocalizedMessage();
                return false;
            }

            //TODO Seated when no controllers

            System.out.println("OpenXR initialized & VR connected.");
            this.deviceVelocity = new Vector3f[64];

            for (int i = 0; i < this.poseMatrices.length; ++i) {
                this.poseMatrices[i] = new Matrix4f();
                this.deviceVelocity[i] = new Vector3f();
            }

            this.initialized = true;
            return true;
        }
    }

    private void initializeOpenXRInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            device.initOpenXRLoader(stack);

            //Check extensions
            IntBuffer numExtensions = stack.callocInt(1);
            int error = XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, numExtensions, null);
            logError(error, "xrEnumerateInstanceExtensionProperties",  "get count");

            XrExtensionProperties.Buffer properties = new XrExtensionProperties.Buffer(
                bufferStack(numExtensions.get(0), XrExtensionProperties.SIZEOF, XR10.XR_TYPE_EXTENSION_PROPERTIES)
            );

            //Load extensions
            error = XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, numExtensions, properties);
            logError(error, "xrEnumerateInstanceExtensionProperties",  "get extensions");

            //get needed extensions
            String graphicsExtension = device.getGraphicsExtension();
            boolean missingGraphics = true;
            PointerBuffer extensions = stack.callocPointer(3);
            while (properties.hasRemaining()) {
                XrExtensionProperties prop = properties.get();
                String extensionName = prop.extensionNameString();
                if (extensionName.equals(graphicsExtension)) {
                    missingGraphics = false;
                    extensions.put(memAddress(stackUTF8(graphicsExtension)));
                }
                if (extensionName.equals(EXTHPMixedRealityController.XR_EXT_HP_MIXED_REALITY_CONTROLLER_EXTENSION_NAME)) {
                    extensions.put(memAddress(stackUTF8(EXTHPMixedRealityController.XR_EXT_HP_MIXED_REALITY_CONTROLLER_EXTENSION_NAME)));
                }
                if (extensionName.equals(HTCViveCosmosControllerInteraction.XR_HTC_VIVE_COSMOS_CONTROLLER_INTERACTION_EXTENSION_NAME)) {
                    extensions.put(memAddress(stackUTF8(HTCViveCosmosControllerInteraction.XR_HTC_VIVE_COSMOS_CONTROLLER_INTERACTION_EXTENSION_NAME)));
                }
            }

            if (missingGraphics) {
                throw new RuntimeException("OpenXR runtime is missing a supported graphics extension.");
            }

            //Create APP info
            XrApplicationInfo applicationInfo = XrApplicationInfo.calloc(stack);
            applicationInfo.apiVersion(XR10.XR_MAKE_VERSION(1, 0, 40));
            applicationInfo.applicationName(stack.UTF8("Vivecraft"));
            applicationInfo.applicationVersion(1);

            //Create instance info
            XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.calloc(stack);
            createInfo.type(XR10.XR_TYPE_INSTANCE_CREATE_INFO);
            createInfo.next(device.getPlatformInfo(stack));
            createInfo.createFlags(0);
            createInfo.applicationInfo(applicationInfo);
            createInfo.enabledApiLayerNames(null);
            createInfo.enabledExtensionNames(extensions.flip());

            //Create XR instance
            PointerBuffer instancePtr = stack.callocPointer(1);
            int xrResult = XR10.xrCreateInstance(createInfo, instancePtr);
            if (xrResult == XR10.XR_ERROR_RUNTIME_FAILURE) {
                throw new RuntimeException("Failed to create xrInstance, are you sure your headset is plugged in?");
            } else if (xrResult == XR10.XR_ERROR_INSTANCE_LOST) {
                throw new RuntimeException("Failed to create xrInstance due to runtime updating");
            } else if (xrResult < 0) {
                throw new RuntimeException("XR method returned " + xrResult);
            }
            instance = new XrInstance(instancePtr.get(0), createInfo);

            this.poseMatrices = new Matrix4f[64];

            for (int i = 0; i < this.poseMatrices.length; ++i) {
                this.poseMatrices[i] = new Matrix4f();
            }

            this.initSuccess = true;
        }
    }

    public static MCOpenXR get() {
        return OME;
    }

    private void initializeOpenXRSession() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            //Create system
            XrSystemGetInfo system = XrSystemGetInfo.calloc(stack);
            system.type(XR10.XR_TYPE_SYSTEM_GET_INFO);
            system.next(NULL);
            system.formFactor(XR10.XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);

            LongBuffer longBuffer = stack.callocLong(1);
            int error = XR10.xrGetSystem(instance, system, longBuffer);
            logError(error, "xrGetSystem",  "");
            this.systemID = longBuffer.get(0);

            if (systemID == 0) {
                throw new RuntimeException("No compatible headset detected");
            }

            XrSystemProperties systemProperties = XrSystemProperties.calloc(stack).type(XR10.XR_TYPE_SYSTEM_PROPERTIES);
            error = XR10.xrGetSystemProperties(instance, systemID, systemProperties);
            MCOpenXR.get().logError(error, "xrGetSystemProperties",  "");
            XrSystemTrackingProperties trackingProperties = systemProperties.trackingProperties();
            XrSystemGraphicsProperties graphicsProperties = systemProperties.graphicsProperties();

            MCOpenXR.get().systemName = memUTF8(memAddress(systemProperties.systemName()));
            int vendor = systemProperties.vendorId();
            boolean orientationTracking = trackingProperties.orientationTracking();
            boolean positionTracking = trackingProperties.positionTracking();
            int maxWidth = graphicsProperties.maxSwapchainImageWidth();
            int maxHeight = graphicsProperties.maxSwapchainImageHeight();
            int maxLayerCount = graphicsProperties.maxLayerCount();

            VRSettings.LOGGER.info("Found device with id:  {}", systemID);
            VRSettings.LOGGER.info("Headset Name: {}, Vendor: {}", MCOpenXR.get().systemName, vendor);
            VRSettings.LOGGER.info("Headset Orientation Tracking: {}, Position Tracking: {}", orientationTracking, positionTracking);
            VRSettings.LOGGER.info("Headset Max Width: {}, Max Height: {}, Max Layer Count: {}", maxWidth, maxHeight, maxLayerCount);

            //Create session
            XrSessionCreateInfo info = XrSessionCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_SESSION_CREATE_INFO);
            info.next(device.checkGraphics(stack, instance, systemID).address());
            info.createFlags(0);
            info.systemId(systemID);

            PointerBuffer sessionPtr = stack.callocPointer(1);
            error = XR10.xrCreateSession(instance, info, sessionPtr);
            logError(error, "xrCreateSession",  "");

            session = new XrSession(sessionPtr.get(0), instance);

            while (!this.isActive) {
                System.out.println("waiting");
                pollVREvents();
            }

        }
    }

    private void initializeOpenXRSpace() {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrPosef identityPose = XrPosef.calloc(stack);
            identityPose.set(
                XrQuaternionf.calloc(stack).set(0, 0, 0, 1),
                XrVector3f.calloc(stack)
            );

            XrReferenceSpaceCreateInfo referenceSpaceCreateInfo = XrReferenceSpaceCreateInfo.calloc(stack);
            referenceSpaceCreateInfo.type(XR10.XR_TYPE_REFERENCE_SPACE_CREATE_INFO);
            referenceSpaceCreateInfo.next(NULL);
            referenceSpaceCreateInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_STAGE);
            referenceSpaceCreateInfo.poseInReferenceSpace(identityPose);

            PointerBuffer pp = stack.callocPointer(1);
            int error = XR10.xrCreateReferenceSpace(session, referenceSpaceCreateInfo, pp);
            xrAppSpace = new XrSpace(pp.get(0), session);
            logError(error, "xrCreateReferenceSpace",  "XR_REFERENCE_SPACE_TYPE_STAGE");

            referenceSpaceCreateInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_VIEW);
            error = XR10.xrCreateReferenceSpace(session, referenceSpaceCreateInfo, pp);
            logError(error, "xrCreateReferenceSpace",  "XR_REFERENCE_SPACE_TYPE_VIEW");
            xrViewSpace = new XrSpace(pp.get(0), session);
        }
    }

    private void initializeOpenXRSwapChain() {
        try (MemoryStack stack = stackPush()) {
            //Check amount of views
            IntBuffer intBuf = stack.callocInt(1);
            int error = XR10.xrEnumerateViewConfigurationViews(instance, systemID,  XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, intBuf, null);
            logError(error, "xrEnumerateViewConfigurationViews",  "get count");

            //Get all views
            ByteBuffer viewConfBuffer = bufferStack(intBuf.get(0), XrViewConfigurationView.SIZEOF, XR10.XR_TYPE_VIEW_CONFIGURATION_VIEW);
            XrViewConfigurationView.Buffer views = new XrViewConfigurationView.Buffer(viewConfBuffer);
            error = XR10.xrEnumerateViewConfigurationViews(instance, systemID,  XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, intBuf, views);
            logError(error, "xrEnumerateViewConfigurationViews",  "get views");
            int viewCountNumber = intBuf.get(0);

            this.viewBuffer = new XrView.Buffer(
                bufferHeap(viewCountNumber, XrView.SIZEOF, XR10.XR_TYPE_VIEW)
            );
            //Check swapchain formats
            error = XR10.xrEnumerateSwapchainFormats(session, intBuf, null);
            logError(error, "xrEnumerateSwapchainFormats",  "get count");

            //Get swapchain formats
            LongBuffer swapchainFormats = stack.callocLong(intBuf.get(0));
            error = XR10.xrEnumerateSwapchainFormats(session, intBuf, swapchainFormats);
            logError(error, "xrEnumerateSwapchainFormats",  "get formats");

            long[] desiredSwapchainFormats = {
                //SRGB formats
                GL21.GL_SRGB8_ALPHA8,
                GL21.GL_SRGB8,
                //others
                GL11.GL_RGB10_A2,
                GL30.GL_RGBA16F,
                GL30.GL_RGB16F,

                // The two below should only be used as a fallback, as they are linear color formats without enough bits for color
                // depth, thus leading to banding.
                GL11.GL_RGBA8,
                GL31.GL_RGBA8_SNORM,
            };

            //Choose format
            long chosenFormat = 0;
            for (long glFormatIter : desiredSwapchainFormats) {
                swapchainFormats.rewind();
                while (swapchainFormats.hasRemaining()) {
                    if (glFormatIter == swapchainFormats.get()) {
                        chosenFormat = glFormatIter;
                        break;
                    }
                }
                if (chosenFormat != 0) {
                    break;
                }
            }

            if (chosenFormat == 0) {
                var formats = new ArrayList<Long>();
                swapchainFormats.rewind();
                while (swapchainFormats.hasRemaining()) {
                    formats.add(swapchainFormats.get());
                }
                throw new RuntimeException("No compatible swapchain / framebuffer format available: " + formats);
            }

            //Make swapchain
            XrViewConfigurationView viewConfig = views.get(0);
            XrSwapchainCreateInfo swapchainCreateInfo = XrSwapchainCreateInfo.calloc(stack);
            swapchainCreateInfo.type(XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO);
            swapchainCreateInfo.next(NULL);
            swapchainCreateInfo.createFlags(0);
            swapchainCreateInfo.usageFlags(XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT);
            swapchainCreateInfo.format(chosenFormat);
            swapchainCreateInfo.sampleCount(1);
            swapchainCreateInfo.width(viewConfig.recommendedImageRectWidth());
            swapchainCreateInfo.height(viewConfig.recommendedImageRectHeight());
            swapchainCreateInfo.faceCount(1);
            swapchainCreateInfo.arraySize(2);
            swapchainCreateInfo.mipCount(1);

            PointerBuffer handlePointer = stack.callocPointer(1);
            error = XR10.xrCreateSwapchain(session, swapchainCreateInfo, handlePointer);
            logError(error, "xrCreateSwapchain",  "format: " + chosenFormat);
            swapchain = new XrSwapchain(handlePointer.get(0), session);
            this.width = swapchainCreateInfo.width();
            this.height = swapchainCreateInfo.height();
        }
    }

    /**
     * Creates an array of XrStructs with their types pre set to @param type
     */
    static ByteBuffer bufferStack(int capacity, int sizeof, int type) {
        ByteBuffer b = stackCalloc(capacity * sizeof);

        for (int i = 0; i < capacity; i++) {
            b.position(i * sizeof);
            b.putInt(type);
        }
        b.rewind();
        return b;
    }

    private void initInputAndApplication() {
        this.populateInputActions();

        //this.generateActionManifest();
        //this.loadActionManifest();
        this.loadActionHandles();
        this.loadDefaultBindings();
        //this.installApplicationManifest(false);
        this.inputInitialized = true;

    }

    @Override
    public Matrix4f getControllerComponentTransform(int var1, String var2) {
        return new Matrix4f();
    }

    @Override
    public boolean hasCameraTracker() {
        return false;
    }

    @Override
    public List<Long> getOrigins(VRInputAction var1) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrBoundSourcesForActionEnumerateInfo info = XrBoundSourcesForActionEnumerateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_BOUND_SOURCES_FOR_ACTION_ENUMERATE_INFO);
            info.next(NULL);
            info.action(new XrAction(var1.handle, new XrActionSet(actionSetHandles.get(var1.actionSet), instance)));
            IntBuffer buf = stack.callocInt(1);
            int error = XR10.xrEnumerateBoundSourcesForAction(session, info, buf, null);
            logError(error, "xrEnumerateBoundSourcesForAction",  var1.name);

            int size = buf.get();
            if (size <= 0) {
                return List.of(0L);
            }

            buf = stack.callocInt(size);
            LongBuffer longbuf = stack.callocLong(size);
            error = XR10.xrEnumerateBoundSourcesForAction(session, info, buf, longbuf);
            logError(error, "xrEnumerateBoundSourcesForAction",  var1.name);
            long[] array;
            if (longbuf.hasArray()) { //TODO really?
                array = longbuf.array();
            } else {
                longbuf.rewind();
                array = new long[longbuf.remaining()];
                int index = 0;
                while (longbuf.hasRemaining()) {
                    array[index++] = longbuf.get();
                }
            }
            return Arrays.stream(array).boxed().toList();
        }
    }

    @Override
    public String getOriginName(long l) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrInputSourceLocalizedNameGetInfo info = XrInputSourceLocalizedNameGetInfo.calloc(stack);
            info.type(XR10.XR_TYPE_INPUT_SOURCE_LOCALIZED_NAME_GET_INFO);
            info.next(0);
            info.sourcePath(l);
            info.whichComponents(XR10.XR_INPUT_SOURCE_LOCALIZED_NAME_COMPONENT_BIT);

            IntBuffer buf = stack.callocInt(1);
            int error = XR10.xrGetInputSourceLocalizedName(session, info, buf, null);
            logError(error, "xrGetInputSourceLocalizedName",  "get length");

            int size = buf.get();
            if (size <= 0) {
                return "";
            }

            buf = stack.callocInt(size);
            ByteBuffer byteBuffer = stack.calloc(size);
            error = XR10.xrGetInputSourceLocalizedName(session, info, buf, byteBuffer);
            logError(error, "xrGetInputSourceLocalizedName",  "get String");
            return new String(byteBuffer.array());
        }
    }

    @Override
    public VRRenderer createVRRenderer() {
        return new OpenXRStereoRenderer(this);
    }

    @Override
    public boolean isActive() {
        this.pollVREvents();
        return isActive;
    }

    @Override
    public ControllerType getOriginControllerType(long i) {
        if (i == aim[RIGHT_CONTROLLER]) {
            return ControllerType.RIGHT;
        }
        return ControllerType.LEFT;
    }

    @Override
    public float getIPD() {
        return this.getEyePosition(RenderPass.RIGHT).x - this.getEyePosition(RenderPass.LEFT).x;
    }

    @Override
    public String getRuntimeName() {
        return "OpenXR";
    }

    private static final String[] BOTH_HANDS = new String[] {"/user/hand/left", "/user/hand/right"};

    //TODO Collect and register all actions
    private void loadActionHandles() {
        for (VRInputActionSet vrinputactionset : VRInputActionSet.values()) {
            long actionSet = makeActionSet(instance, vrinputactionset.name, vrinputactionset.localizedName, 0);
            this.actionSetHandles.put(vrinputactionset, actionSet);
        }

        for (VRInputAction vrinputaction : this.inputActions.values()) {
            long action = createAction(vrinputaction.name, vrinputaction.name, vrinputaction.type, new XrActionSet(this.actionSetHandles.get(vrinputaction.actionSet), instance), BOTH_HANDS);
            vrinputaction.setHandle(action);
        }

        setupControllers();

        XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
        this.haptics[RIGHT_CONTROLLER] = createAction("/actions/global/out/righthaptic", "/actions/global/out/righthaptic", "haptic", actionSet, BOTH_HANDS);
        this.haptics[LEFT_CONTROLLER] = createAction("/actions/global/out/lefthaptic", "/actions/global/out/lefthaptic", "haptic", actionSet, BOTH_HANDS);

    }

    private void setupControllers() {
        XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
        this.grip[RIGHT_CONTROLLER] = createAction("/actions/global/in/righthand", "/actions/global/in/righthand", "pose", actionSet, BOTH_HANDS);
        this.grip[LEFT_CONTROLLER] = createAction("/actions/global/in/lefthand", "/actions/global/in/lefthand", "pose", actionSet, BOTH_HANDS);
        this.aim[RIGHT_CONTROLLER] = createAction("/actions/global/in/righthandaim", "/actions/global/in/righthandaim", "pose", actionSet, BOTH_HANDS);
        this.aim[LEFT_CONTROLLER] = createAction("/actions/global/in/lefthandaim", "/actions/global/in/lefthandaim", "pose", actionSet, BOTH_HANDS);
    }

    private void loadDefaultBindings() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int error;
            for (String headset: XRBindings.supportedHeadsets()) {
                VRSettings.LOGGER.info("loading defaults for {}", headset);
                Pair<String, String>[] defaultBindings = XRBindings.getBinding(headset).toArray(new Pair[0]);
                XrActionSuggestedBinding.Buffer bindings = XrActionSuggestedBinding.calloc(defaultBindings.length + 6, stack); //TODO different way of adding controller poses

                for (int i = 0; i < defaultBindings.length; i++) {
                    Pair<String, String> pair = defaultBindings[i];
                    VRInputAction binding = this.getInputActionByName(pair.getLeft());
                    if (binding.handle == 0L) {
                        VRSettings.LOGGER.error("Handle for '{}'/'{}' is null", pair.getLeft(), pair.getRight());
                        continue;
                    }
                    bindings.get(i).set(
                        new XrAction(binding.handle, new XrActionSet(actionSetHandles.get(binding.actionSet), instance)),
                        getPath(pair.getRight())
                    );
                }

                //TODO make this also changeable?
                XrActionSet actionSet = new XrActionSet(actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
                bindings.get(defaultBindings.length).set(
                    new XrAction(this.grip[RIGHT_CONTROLLER], actionSet),
                    getPath("/user/hand/right/input/grip/pose")
                );
                bindings.get(defaultBindings.length + 1).set(
                    new XrAction(this.grip[LEFT_CONTROLLER], actionSet),
                    getPath("/user/hand/left/input/grip/pose")
                );
                bindings.get(defaultBindings.length + 2).set(
                    new XrAction(this.aim[RIGHT_CONTROLLER], actionSet),
                    getPath("/user/hand/right/input/aim/pose")
                );
                bindings.get(defaultBindings.length + 3).set(
                    new XrAction(this.aim[LEFT_CONTROLLER], actionSet),
                    getPath("/user/hand/left/input/aim/pose")
                );

                bindings.get(defaultBindings.length + 4).set(
                    new XrAction(this.haptics[RIGHT_CONTROLLER], actionSet),
                    getPath("/user/hand/right/output/haptic")
                );

                bindings.get(defaultBindings.length + 5).set(
                    new XrAction(this.haptics[LEFT_CONTROLLER], actionSet),
                    getPath("/user/hand/left/output/haptic")
                );

                XrInteractionProfileSuggestedBinding suggested_binds = XrInteractionProfileSuggestedBinding.calloc(stack);
                suggested_binds.type(XR10.XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING);
                suggested_binds.next(NULL);
                suggested_binds.interactionProfile(getPath(headset));
                suggested_binds.suggestedBindings(bindings);

                error = XR10.xrSuggestInteractionProfileBindings(instance, suggested_binds);
                logError(error, "xrSuggestInteractionProfileBindings",  headset);
            }


            XrSessionActionSetsAttachInfo attach_info = XrSessionActionSetsAttachInfo.calloc(stack);
            attach_info.type(XR10.XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO);
            attach_info.next(NULL);
            attach_info.actionSets(stackPointers(actionSetHandles.values().stream().mapToLong(value -> value).toArray()));

            error = XR10.xrAttachSessionActionSets(session, attach_info);
            logError(error, "xrAttachSessionActionSets",  "");

            XrActionSet actionSet = new XrActionSet(this.actionSetHandles.get(VRInputActionSet.GLOBAL), instance);
            XrActionSpaceCreateInfo actionSpace = XrActionSpaceCreateInfo.calloc(stack);
            actionSpace.type(XR10.XR_TYPE_ACTION_SPACE_CREATE_INFO);
            actionSpace.next(NULL);
            actionSpace.action(new XrAction(grip[RIGHT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/right"));
            actionSpace.poseInActionSpace(POSE_IDENTITY);
            PointerBuffer pp = stackCallocPointer(1);
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "grip: /user/hand/right");
            this.gripSpace[RIGHT_CONTROLLER] = new XrSpace(pp.get(0), session);

            actionSpace.action(new XrAction(grip[LEFT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/left"));
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "grip: /user/hand/left");
            this.gripSpace[LEFT_CONTROLLER] = new XrSpace(pp.get(0), session);

            actionSpace.action(new XrAction(aim[RIGHT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/right"));
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "aim: /user/hand/right");
            this.aimSpace[RIGHT_CONTROLLER] = new XrSpace(pp.get(0), session);

            actionSpace.action(new XrAction(aim[LEFT_CONTROLLER], actionSet));
            actionSpace.subactionPath(getPath("/user/hand/left"));
            error = XR10.xrCreateActionSpace(session, actionSpace, pp);
            logError(error, "xrCreateActionSpace",  "aim: /user/hand/left");
            this.aimSpace[LEFT_CONTROLLER] = new XrSpace(pp.get(0), session);

        }
    }

    public long getPath(String pathString) {
        return this.paths.computeIfAbsent(pathString, s -> {
            try (MemoryStack ignored = stackPush()) {
                LongBuffer buf = stackCallocLong(1);
                int error = XR10.xrStringToPath(instance, pathString, buf);
                logError(error, "getPath",  pathString);
                return buf.get();
            }
        });
    }

    private long createAction(String name, String localisedName, String type, XrActionSet actionSet, @Nullable String[] subactionPaths) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            String s = name.split("/")[name.split("/").length -1].toLowerCase();
            XrActionCreateInfo hands = XrActionCreateInfo.calloc(stack);
            hands.type(XR10.XR_TYPE_ACTION_CREATE_INFO);
            hands.next(NULL);
            hands.actionName(memUTF8(s));
            switch (type) {
                case "boolean" -> hands.actionType(XR10.XR_ACTION_TYPE_BOOLEAN_INPUT);
                case "vector1" -> hands.actionType(XR10.XR_ACTION_TYPE_FLOAT_INPUT);
                case "vector2" -> hands.actionType(XR10.XR_ACTION_TYPE_VECTOR2F_INPUT);
                case "pose" -> hands.actionType(XR10.XR_ACTION_TYPE_POSE_INPUT);
                case "haptic" -> hands.actionType(XR10.XR_ACTION_TYPE_VIBRATION_OUTPUT);
            }
            if(subactionPaths != null) {
                LongBuffer buffer = stackCallocLong(subactionPaths.length);
                for(String path : subactionPaths) {
                    buffer.put(getPath(path));
                }
                hands.countSubactionPaths(subactionPaths.length);
                hands.subactionPaths(buffer.rewind());
            } else {
                hands.countSubactionPaths(0);
                hands.subactionPaths(null);
            }
            hands.localizedActionName(memUTF8(s));
            PointerBuffer buffer = stackCallocPointer(1);

            int error = XR10.xrCreateAction(actionSet, hands, buffer);
            logError(error, "xrCreateAction",  "name:", name, "type:", type);
            return buffer.get(0);
        }
    }

    private long makeActionSet(XrInstance instance, String name, String localisedName, int priority) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            XrActionSetCreateInfo info = XrActionSetCreateInfo.calloc(stack);
            info.type(XR10.XR_TYPE_ACTION_SET_CREATE_INFO);
            info.next(NULL);
            info.actionSetName(memUTF8(localisedName.toLowerCase()));
            info.localizedActionSetName(memUTF8(localisedName.toLowerCase()));
            info.priority(priority);
            PointerBuffer buffer = stack.callocPointer(1);

            int error = XR10.xrCreateActionSet(instance, info, buffer);
            logError(error, "makeActionSet", localisedName.toLowerCase());
            return buffer.get(0);
        }
    }

    static ByteBuffer bufferHeap(int capacity, int sizeof, int type) {
        ByteBuffer b = memCalloc(capacity * sizeof);

        for (int i = 0; i < capacity; i++) {
            b.position(i * sizeof);
            b.putInt(type);
        }
        b.rewind();
        return b;
    }

    /**
     * gets the String for the given xrResult
     */
    private String getResultName(int xrResult) {
        String resultString = switch (xrResult) {
            case 1 -> "XR_TIMEOUT_EXPIRED";
            case 3 -> "XR_SESSION_LOSS_PENDING";
            case 4 -> "XR_EVENT_UNAVAILABLE";
            case 7 -> "XR_SPACE_BOUNDS_UNAVAILABLE";
            case 8 -> "XR_SESSION_NOT_FOCUSED";
            case 9 -> "XR_FRAME_DISCARDED";
            case -1 -> "XR_ERROR_VALIDATION_FAILURE";
            case -2 -> "XR_ERROR_RUNTIME_FAILURE";
            case -3 -> "XR_ERROR_OUT_OF_MEMORY";
            case -4 -> "XR_ERROR_API_VERSION_UNSUPPORTED";
            case -6 -> "XR_ERROR_INITIALIZATION_FAILED";
            case -7 -> "XR_ERROR_FUNCTION_UNSUPPORTED";
            case -8 -> "XR_ERROR_FEATURE_UNSUPPORTED";
            case -9 -> "XR_ERROR_EXTENSION_NOT_PRESENT";
            case -10 -> "XR_ERROR_LIMIT_REACHED";
            case -11 -> "XR_ERROR_SIZE_INSUFFICIENT";
            case -12 -> "XR_ERROR_HANDLE_INVALID";
            case -13 -> "XR_ERROR_INSTANCE_LOST";
            case -14 -> "XR_ERROR_SESSION_RUNNING";
            case -16 -> "XR_ERROR_SESSION_NOT_RUNNING";
            case -17 -> "XR_ERROR_SESSION_LOST";
            case -18 -> "XR_ERROR_SYSTEM_INVALID";
            case -19 -> "XR_ERROR_PATH_INVALID";
            case -20 -> "XR_ERROR_PATH_COUNT_EXCEEDED";
            case -21 -> "XR_ERROR_PATH_FORMAT_INVALID";
            case -22 -> "XR_ERROR_PATH_UNSUPPORTED";
            case -23 -> "XR_ERROR_LAYER_INVALID";
            case -24 -> "XR_ERROR_LAYER_LIMIT_EXCEEDED";
            case -25 -> "XR_ERROR_SWAPCHAIN_RECT_INVALID";
            case -26 -> "XR_ERROR_SWAPCHAIN_FORMAT_UNSUPPORTED";
            case -27 -> "XR_ERROR_ACTION_TYPE_MISMATCH";
            case -28 -> "XR_ERROR_SESSION_NOT_READY";
            case -29 -> "XR_ERROR_SESSION_NOT_STOPPING";
            case -30 -> "XR_ERROR_TIME_INVALID";
            case -31 -> "XR_ERROR_REFERENCE_SPACE_UNSUPPORTED";
            case -32 -> "XR_ERROR_FILE_ACCESS_ERROR";
            case -33 -> "XR_ERROR_FILE_CONTENTS_INVALID";
            case -34 -> "XR_ERROR_FORM_FACTOR_UNSUPPORTED";
            case -35 -> "XR_ERROR_FORM_FACTOR_UNAVAILABLE";
            case -36 -> "XR_ERROR_API_LAYER_NOT_PRESENT";
            case -37 -> "XR_ERROR_CALL_ORDER_INVALID";
            case -38 -> "XR_ERROR_GRAPHICS_DEVICE_INVALID";
            case -39 -> "XR_ERROR_POSE_INVALID";
            case -40 -> "XR_ERROR_INDEX_OUT_OF_RANGE";
            case -41 -> "XR_ERROR_VIEW_CONFIGURATION_TYPE_UNSUPPORTED";
            case -42 -> "XR_ERROR_ENVIRONMENT_BLEND_MODE_UNSUPPORTED";
            case -44 -> "XR_ERROR_NAME_DUPLICATED";
            case -45 -> "XR_ERROR_NAME_INVALID";
            case -46 -> "XR_ERROR_ACTIONSET_NOT_ATTACHED";
            case -47 -> "XR_ERROR_ACTIONSETS_ALREADY_ATTACHED";
            case -48 -> "XR_ERROR_LOCALIZED_NAME_DUPLICATED";
            case -49 -> "XR_ERROR_LOCALIZED_NAME_INVALID";
            case -50 -> "XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING";
            case -51 -> "XR_ERROR_RUNTIME_UNAVAILABLE";
            default -> null;
        };
        if (resultString == null) {
            // ask the runtime for the xrResult name
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer str = stack.calloc(XR10.XR_MAX_RESULT_STRING_SIZE);

                if (XR10.xrResultToString(instance, xrResult, str) == XR10.XR_SUCCESS) {
                    resultString = (memUTF8(memAddress(str)));
                } else {
                    resultString = "Unknown Error: " + xrResult;
                }
            }
        }
        return resultString;
    }

    /**
     * logs only errors
     * @param xrResult result to check
     * @param caller where the xrResult came from
     * @param args arguments may be helpful in locating the error
     */
    protected void logError(int xrResult, String caller, String... args) {
        if (xrResult < 0) {
            VRSettings.LOGGER.error("{} for {} errored: {}", caller, String.join(" ", args), getResultName(xrResult));
        }
    }
}
