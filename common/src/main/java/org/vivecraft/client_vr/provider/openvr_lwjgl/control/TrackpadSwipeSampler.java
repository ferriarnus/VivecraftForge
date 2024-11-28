package org.vivecraft.client_vr.provider.openvr_lwjgl.control;

import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.openvr_lwjgl.MCOpenVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.common.utils.lwjgl.Vector2f;
import org.vivecraft.common.utils.math.Vector2;

public class TrackpadSwipeSampler {
    private static final int UP = 0;
    private static final int RIGHT = 1;
    private static final int DOWN = 2;
    private static final int LEFT = 3;
    private static final float THRESHOLD = 0.5F;

    private final Vector2f[] buffer = new Vector2f[5];
    private int index;
    private long count;
    private final Vector2f accumulator = new Vector2f();
    private final int[] swiped = new int[4];

    public TrackpadSwipeSampler() {
        for (int i = 0; i < this.buffer.length; ++i) {
            this.buffer[i] = new Vector2f();
        }
    }

    public void update(ControllerType hand, Vector2 position) {
        VRInputAction trackpad = MCOpenVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyTrackpadTouch);
        trackpad.setCurrentHand(hand);

        if (trackpad.isButtonPressed()) {
            this.buffer[this.index].set(position.getX(), position.getY());

            if (++this.index >= this.buffer.length) {
                this.index = 0;
            }

            this.count++;
        } else {
            // reset buffer
            for (Vector2f vec : this.buffer) {
                vec.set(0.0F, 0.0F);
            }
            this.count = 0L;
        }

        // wait till te buffer is full, so we start tracking where the touch started
        if (this.count >= this.buffer.length) {
            // the index is always at the oldest data point at this moment
            int nextIndex = (this.index + 1) % this.buffer.length;
            this.accumulator.x += this.buffer[nextIndex].x - this.buffer[this.index].x;
            this.accumulator.y += this.buffer[nextIndex].y - this.buffer[this.index].y;

            if (this.accumulator.x >= THRESHOLD) {
                this.accumulator.x -= THRESHOLD;
                this.swiped[RIGHT]++;
            }

            if (this.accumulator.x <= -THRESHOLD) {
                this.accumulator.x += THRESHOLD;
                this.swiped[LEFT]++;
            }

            if (this.accumulator.y >= THRESHOLD) {
                this.accumulator.y -= THRESHOLD;
                this.swiped[UP]++;
            }

            if (this.accumulator.y <= -THRESHOLD) {
                this.accumulator.y += THRESHOLD;
                this.swiped[DOWN]++;
            }
        } else {
            this.accumulator.set(0.0F, 0.0F);
        }
    }

    public boolean isSwipedLeft() {
        return this.isSwiped(LEFT);
    }

    public boolean isSwipedRight() {
        return this.isSwiped(RIGHT);
    }

    public boolean isSwipedUp() {
        return this.isSwiped(UP);
    }

    public boolean isSwipedDown() {
        return this.isSwiped(DOWN);
    }

    private boolean isSwiped(int direction) {
        if (this.swiped[direction] > 0) {
            this.swiped[direction]--;
            return true;
        } else {
            return false;
        }
    }
}
