package org.vivecraft.client_vr.provider;

/**
 * holds a device source and device index
 */
public class DeviceSource {
    private final static int INVALID_DEVICE = -1;

    public int deviceIndex = INVALID_DEVICE;
    public Source source;

    public DeviceSource(Source source) {
        this.source = source;
    }

    public DeviceSource(Source source, int deviceIndex) {
        set(source, deviceIndex);
    }

    public void set(DeviceSource other) {
        this.source = other.source;
        this.deviceIndex = other.deviceIndex;
    }

    public void set(Source source, int deviceIndex) {
        this.source = source;
        this.deviceIndex = deviceIndex;
    }

    public void reset() {
        this.source = Source.NULL;
        this.deviceIndex = INVALID_DEVICE;
    }

    public boolean isValid() {
        return this.deviceIndex != INVALID_DEVICE;
    }

    public boolean is(Source source, int index) {
        return this.source == source && this.deviceIndex == index;
    }

    @Override
    public String toString() {
        return this.source + ": " + this.deviceIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        DeviceSource that = (DeviceSource) o;
        return this.deviceIndex == that.deviceIndex && this.source == that.source;
    }

    public enum Source {
        NULL,
        OPENVR,
        OSC
    }
}
