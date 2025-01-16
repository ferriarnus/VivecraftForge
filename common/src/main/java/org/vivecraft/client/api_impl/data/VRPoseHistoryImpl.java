package org.vivecraft.client.api_impl.data;

import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.client.data.VRPoseHistory;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.DoubleStream;

public class VRPoseHistoryImpl implements VRPoseHistory {

    private final LinkedList<VRPose> dataQueue = new LinkedList<>();

    public VRPoseHistoryImpl() {
    }

    public void addPose(VRPose pose) {
        this.dataQueue.addFirst(pose);
        if (this.dataQueue.size() > VRPoseHistory.MAX_TICKS_BACK + 1) {
            this.dataQueue.removeLast();
        }
    }

    public void clear() {
        this.dataQueue.clear();
    }

    @Override
    public int ticksOfHistory() {
        return this.dataQueue.size();
    }

    @Override
    public List<VRPose> getAllHistoricalData() throws IllegalArgumentException {
        return List.copyOf(this.dataQueue);
    }

    @Override
    public VRPose getHistoricalData(int ticksBack) throws IllegalArgumentException, IllegalStateException {
        checkTicksBack(ticksBack);
        if (this.dataQueue.size() <= ticksBack) {
            throw new IllegalStateException("Cannot retrieve data from " + ticksBack + " ticks ago, when there is " +
                "only data for up to " + (this.dataQueue.size() - 1) + " ticks ago.");
        }
        return this.dataQueue.get(ticksBack);
    }

    @Override
    public Vec3 netMovement(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        checkPartNonNull(bodyPart);
        checkTicksBack(maxTicksBack);
        VRBodyPartData currentData = this.dataQueue.getLast().getBodyPartData(bodyPart);
        if (currentData == null) {
            return null;
        }
        Vec3 current = currentData.getPos();
        VRBodyPartData oldData = getOldPose(maxTicksBack).getBodyPartData(bodyPart);
        if (oldData == null) {
            return null;
        }
        Vec3 old = oldData.getPos();
        return current.subtract(old);
    }

    @Override
    public Vec3 averageVelocity(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        checkPartNonNull(bodyPart);
        checkTicksBack(maxTicksBack);
        maxTicksBack = getNumTicksBack(maxTicksBack);
        List<Vec3> diffs = new ArrayList<>(maxTicksBack);
        for (int i = 0; i < maxTicksBack; i++) {
            VRBodyPartData newer = dataQueue.get(i).getBodyPartData(bodyPart);
            VRBodyPartData older = dataQueue.get(i + 1).getBodyPartData(bodyPart);
            if (newer == null || older == null) {
                break;
            }
            diffs.add(newer.getPos().subtract(older.getPos()));
        }
        if (diffs.isEmpty()) {
            // Return no change if the body part is available but no historical data or null if body part isn't
            // available.
            return dataQueue.getFirst().getBodyPartData(bodyPart) != null ? Vec3.ZERO : null;
        }
        return new Vec3(
            diffs.stream().mapToDouble(vec -> vec.x).average().orElse(0),
            diffs.stream().mapToDouble(vec -> vec.y).average().orElse(0),
            diffs.stream().mapToDouble(vec -> vec.z).average().orElse(0)
        );
    }

    @Override
    public double averageSpeed(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        checkPartNonNull(bodyPart);
        checkTicksBack(maxTicksBack);
        maxTicksBack = getNumTicksBack(maxTicksBack);
        List<Double> speeds = new ArrayList<>(maxTicksBack);
        for (int i = 0; i < maxTicksBack; i++) {
            VRBodyPartData newer = dataQueue.get(i).getBodyPartData(bodyPart);
            VRBodyPartData older = dataQueue.get(i + 1).getBodyPartData(bodyPart);
            if (newer == null || older == null) {
                break;
            }
            speeds.add(newer.getPos().distanceTo(older.getPos()));
        }
        return speeds.stream().mapToDouble(Double::valueOf).average().orElse(0);
    }

    @Override
    public Vec3 averagePosition(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        checkPartNonNull(bodyPart);
        checkTicksBack(maxTicksBack);
        maxTicksBack = getNumTicksBack(maxTicksBack);
        List<Vec3> positions = new ArrayList<>(maxTicksBack);
        for (VRPose pose : dataQueue) {
            VRBodyPartData data = pose.getBodyPartData(bodyPart);
            if (data == null) {
                break;
            }
            positions.add(data.getPos());
        }
        if (positions.isEmpty()) {
            return null;
        }
        return new Vec3(
            positions.stream().mapToDouble(vec -> vec.x).average().orElse(0),
            positions.stream().mapToDouble(vec -> vec.y).average().orElse(0),
            positions.stream().mapToDouble(vec -> vec.z).average().orElse(0)
        );
    }

    private void checkTicksBack(int ticksBack) {
        if (ticksBack < 0 || ticksBack > VRPoseHistory.MAX_TICKS_BACK) {
            throw new IllegalArgumentException("Value must be between 0 and " + VRPoseHistory.MAX_TICKS_BACK + ".");
        }
    }

    private void checkPartNonNull(VRBodyPart bodyPart) {
        if (bodyPart == null) {
            throw new IllegalArgumentException("Cannot get data for a null body part!");
        }
    }

    private VRPose getOldPose(int maxTicksBack) {
        if (this.dataQueue.size() <= maxTicksBack) {
            return this.dataQueue.getFirst();
        } else {
            return this.dataQueue.get(this.dataQueue.size() - maxTicksBack - 1);
        }
    }

    private int getNumTicksBack(int maxTicksBack) {
        if (this.dataQueue.size() <= maxTicksBack) {
            return this.dataQueue.size() - 1;
        } else {
            return maxTicksBack;
        }
    }
}
