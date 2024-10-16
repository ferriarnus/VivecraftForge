package org.vivecraft.client_vr;

import net.minecraft.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.LinkedList;
import java.util.ListIterator;

public class Vector3fHistory {
    private static final int CAPACITY = 450;
    private final LinkedList<Entry> data = new LinkedList<>();

    /**
     * adds a new entry with the given Vec3
     * @param in Vector3fc to add
     */
    public void add(Vector3fc in) {
        this.data.add(new Entry(in));

        if (this.data.size() > CAPACITY) {
            this.data.removeFirst();
        }
    }

    /**
     * clears all data
     */
    public void clear() {
        this.data.clear();
    }

    /**
     * @return the newest Vector3f
     */
    public Vector3fc latest() {
        return (this.data.getLast()).vec;
    }

    /**
     * Get the total integrated device translation for the specified time period.
     * this is the sum of the distance of all points to the oldest point in the timeframe
     * @param seconds time period
     * @return distance in meters
     */
    public float totalMovement(double seconds) {
        long now = Util.getMillis();
        ListIterator<Entry> iterator = this.data.listIterator(this.data.size());
        Entry last = null;
        float distance = 0.0F;

        while (iterator.hasPrevious()) {
            Entry current = iterator.previous();

            if (now - current.ts > seconds * 1000.0D) {
                break;
            } else {
                if (last == null) {
                    last = current;
                } else {
                    distance += last.vec.distance(current.vec);
                }
            }
        }

        return distance;
    }

    /**
     * Get the vector representing the difference in position from now to {@code seconds} ago.
     * @param seconds time period
     * @return vector with the position difference
     */
    public Vector3f netMovement(double seconds) {
        long now = Util.getMillis();
        ListIterator<Entry> iterator = this.data.listIterator(this.data.size());
        Entry last = null;
        Entry first = null;

        while (iterator.hasPrevious()) {
            Entry current = iterator.previous();

            if (now - current.ts > seconds * 1000.0D) {
                break;
            }

            if (last == null) {
                last = current;
            } else {
                first = current;
            }
        }

        return last != null && first != null ? last.vec.sub(first.vec, new Vector3f()) : new Vector3f();
    }

    /**
     * Get the average speed of the device over the specified time period.
     * @param seconds time period
     * @return speed in m/s.
     */
    public float averageSpeed(double seconds) {
        long now = Util.getMillis();
        ListIterator<Entry> iterator = this.data.listIterator(this.data.size());
        float speedTotal = 0.0F;
        Entry last = null;
        int count = 0;

        while (iterator.hasPrevious()) {
            Entry current = iterator.previous();

            if (now - current.ts > seconds * 1000.0D) {
                break;
            }

            if (last == null) {
                last = current;
            } else {
                count++;
                float timeDelta = 0.001F * (last.ts - current.ts);
                float positionDelta = last.vec.distance(current.vec);
                speedTotal += positionDelta / timeDelta;
            }
        }

        return count == 0 ? speedTotal : speedTotal / (float) count;
    }

    /**
     * Get the average position for the last {@code seconds}.
     * @param seconds time period
     * @return average position
     */
    public Vector3f averagePosition(double seconds) {
        long now = Util.getMillis();
        ListIterator<Entry> iterator = this.data.listIterator(this.data.size());
        Vector3f vec3 = new Vector3f();
        int count = 0;

        while (iterator.hasPrevious()) {
            Entry current = iterator.previous();

            if (now - current.ts > seconds * 1000.0D) {
                break;
            }

            vec3.add(current.vec);
            count++;
        }

        return count == 0 ? vec3 : vec3.div(count);
    }

    /**
     * Entry holding a position and timestamp
     */
    private record Entry(Vector3fc vec, long ts) {
        public Entry(Vector3fc vec) {
            this(vec, Util.getMillis());
        }
    }
}
