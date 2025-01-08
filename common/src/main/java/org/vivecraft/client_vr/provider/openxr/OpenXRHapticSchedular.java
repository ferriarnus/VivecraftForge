package org.vivecraft.client_vr.provider.openxr;

import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.HapticScheduler;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;

import java.util.concurrent.TimeUnit;

import static java.sql.Types.NULL;

public class OpenXRHapticSchedular extends HapticScheduler {

    private void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
        try (MemoryStack stack = MemoryStack.stackPush()){
            int i = controller == ControllerType.RIGHT ? 0 : 1;
            if (ClientDataHolderVR.getInstance().vrSettings.reverseHands) {
                i = controller == ControllerType.RIGHT ? 1 : 0;
            }
            XrActionSet actionSet = new XrActionSet(MCOpenXR.get().getActionSetHandle(VRInputActionSet.GLOBAL), MCOpenXR.get().instance);
            XrHapticActionInfo info = XrHapticActionInfo.calloc(stack);
            info.type(XR10.XR_TYPE_HAPTIC_ACTION_INFO);
            info.next(NULL);
            info.action(new XrAction(MCOpenXR.get().haptics[i], actionSet));

            XrHapticVibration vibration = XrHapticVibration.calloc(stack);
            vibration.type(XR10.XR_TYPE_HAPTIC_VIBRATION);
            vibration.next(NULL);
            vibration.duration((long) (durationSeconds * 1_000_000_000));
            vibration.frequency(frequency);
            vibration.amplitude(amplitude);

            int error = XR10.xrApplyHapticFeedback(MCOpenXR.get().session, info, XrHapticBaseHeader.create(vibration));
            MCOpenXR.get().logError(error, "xrApplyHapticFeedback", "");
        }
    }

    @Override
    public void queueHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds) {
        this.executor.schedule(() ->
        {
            this.triggerHapticPulse(controller, durationSeconds, frequency, amplitude);
        }, (long) (delaySeconds * 1000000.0F), TimeUnit.MICROSECONDS);
    }
}
