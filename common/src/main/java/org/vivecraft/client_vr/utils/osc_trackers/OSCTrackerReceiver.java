package org.vivecraft.client_vr.utils.osc_trackers;

import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.messageselector.OSCPatternAddressMessageSelector;
import com.illposed.osc.transport.OSCPortIn;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.utils.MathUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * receives tracker information over the OSC protocol, using the <a href="https://docs.vrchat.com/docs/osc-trackers">VR Chat spec</a>
 */
public class OSCTrackerReceiver {

    private OSCPortIn portIn;

    // tracker data, 0 is head, 1-8 are the trackers
    public final OSCTracker[] trackers = new OSCTracker[8];
    private final MCVR mcvr;

    private final Vector3f offset = new Vector3f();
    private float rotationOffset = 0f;
    private long headTimestamp = 0L;

    public OSCTrackerReceiver(MCVR mcvr) {
        this.mcvr = mcvr;
        for(int i = 0; i < this.trackers.length; i++) {
            this.trackers[i] = new OSCTracker();
        }
        changePort(ClientDataHolderVR.getInstance().vrSettings.oscTrackerPort);
    }

    public void changePort(int port) {
        stop();
        try {
            this.portIn = new OSCPortIn(port);
            VRSettings.LOGGER.info("Vivecraft: start listening for OSC trackers on port: {}", port);
            this.portIn.getDispatcher()
                .addListener(new OSCPatternAddressMessageSelector("/tracking/trackers/*/position"),
                    this::handlePositionMessage);
            this.portIn.getDispatcher()
                .addListener(new OSCPatternAddressMessageSelector("/tracking/trackers/*/rotation"),
                    this::handleRotationMessage);
            this.portIn.startListening();
        } catch (IOException e) {
            VRSettings.LOGGER.error("Vivecraft: Failed to start OSC receiver", e);
        }
    }

    public void stop() {
        if (this.portIn != null) {
            this.portIn.stopListening();
            this.portIn = null;
        }
    }

    public boolean hasTrackers() {
        return Arrays.stream(this.trackers).anyMatch(OSCTracker::isTracking);
    }

    private void handlePositionMessage(OSCMessageEvent event) {
        int index = getMessageTrackerIndex(event.getMessage().getAddress());
        List<Object> args = event.getMessage().getArguments();

        if (index == 0) {
            // head stores the offset to the room center
            this.offset.set(-(float) args.get(0), -(float) args.get(1), (float) args.get(2));
        } else {
            OSCTracker target = this.trackers[index - 1];

            Vector3f translation = new Vector3f((float) args.get(0), (float) args.get(1), -(float) args.get(2));
            translation.add(this.offset);
            translation.rotateY(this.rotationOffset);
            if (!this.mcvr.hmdPivotHistory.isEmpty()) {
                translation.add(this.mcvr.hmdPivotHistory.latest());
            }
            target.pose.setTranslation(translation);

            target.timeStamp = System.currentTimeMillis();
        }
    }

    private void handleRotationMessage(OSCMessageEvent event) {
        int index = getMessageTrackerIndex(event.getMessage().getAddress());

        List<Object> args = event.getMessage().getArguments();

        if (index == 0) {
            // head stores the rotation offset to the room space
            // trackers are also player local, not room local

            Vector3f hmdDir = this.mcvr.getHmdVector();

            Vector3f fwd = new Matrix4f()
                .rotationZ(Mth.DEG_TO_RAD * -(float) args.get(2))
                .rotateX(Mth.DEG_TO_RAD * (float) args.get(0))
                .rotateY(Mth.DEG_TO_RAD * (float) args.get(1))
                .transformDirection(MathUtils.BACK, new Vector3f());

            float newRot = (float) Math.atan2(-fwd.x, fwd.z) - (float) Math.atan2(-hmdDir.x, hmdDir.z);
            if (System.currentTimeMillis() - this.headTimestamp < 300) {
                // head rotation is smoothed, according to vrchat spec
                this.rotationOffset = MathUtils.rotLerpRad(0.01F, this.rotationOffset, newRot);
            } else {
                // for large gaps, reset
                this.rotationOffset = newRot;
            }

            this.headTimestamp = System.currentTimeMillis();
        } else {
            OSCTracker target = this.trackers[index - 1];
            float x = target.pose.m30(), y = target.pose.m31(), z = target.pose.m32();
            target.pose
                .rotationZ(Mth.DEG_TO_RAD * -(float) args.get(2))
                .rotateX(Mth.DEG_TO_RAD * (float) args.get(0))
                .rotateY(Mth.DEG_TO_RAD * (float) args.get(1))
                // the room offset direction is separate and is a rotation around the global Y axis
                .rotateLocalY(this.rotationOffset)
                .setTranslation(x, y, z);

            target.timeStamp = System.currentTimeMillis();
        }
    }

    private int getMessageTrackerIndex(String address) {
        char tracker = address.charAt(19);
        if (tracker == 'h') {
            // head
            return 0;
        }
        return Integer.parseInt(String.valueOf(tracker));
    }

}
