package org.vivecraft.client_vr.provider.openvr_lwjgl;

import org.lwjgl.openvr.VR;
import org.lwjgl.openvr.VRInput;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.HapticScheduler;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.concurrent.TimeUnit;

public class OpenVRHapticScheduler extends HapticScheduler {
    private void triggerHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude) {
        int error = VRInput.VRInput_TriggerHapticVibrationAction(MCOpenVR.get().getHapticHandle(controller), 0.0F, durationSeconds, frequency, amplitude, 0L);

        if (error != VR.EVRInputError_VRInputError_None) {
            VRSettings.LOGGER.error("Vivecraft: Error triggering haptic: {}", MCOpenVR.getInputErrorName(error));
        }
    }

    @Override
    public void queueHapticPulse(ControllerType controller, float durationSeconds, float frequency, float amplitude, float delaySeconds) {
        this.executor.schedule(() ->
            this.triggerHapticPulse(controller, durationSeconds, frequency, amplitude), (long) (delaySeconds * 1000000.0F), TimeUnit.MICROSECONDS);
    }
}
