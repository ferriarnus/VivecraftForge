package org.vivecraft.client.api_impl.data;

import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.client.data.VRPoseHistory;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRPose;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class VRPoseHistoryImpl implements VRPoseHistory {

    private final LinkedList<VRPose> dataQueue = new LinkedList<>();

    public VRPoseHistoryImpl() {
    }

    public void addPose(VRPose pose) {
        this.dataQueue.addFirst(pose);
        if (this.dataQueue.size() > VRPoseHistory.MAX_TICKS_BACK) {
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
    public Vec3 netMovement(int deviceNum, int maxTicksBack) throws IllegalArgumentException {
        checkTicksBack(maxTicksBack);
        Vec3 current = getPart(this.dataQueue.getLast(), deviceNum).getPos();
        Vec3 old = getPart(getOldPose(maxTicksBack), deviceNum).getPos();
        return current.subtract(old);
    }

    @Override
    public Vec3 averageVelocity(int deviceNum, int maxTicksBack) throws IllegalArgumentException {
        checkTicksBack(maxTicksBack);
        Vec3 current = getPart(this.dataQueue.getLast(), deviceNum).getPos();
        Vec3 old = getPart(getOldPose(maxTicksBack), deviceNum).getPos();
        return current.subtract(old).scale(1d / getNumTicksBack(maxTicksBack));
    }

    @Override
    public Vec3 averagePosition(int deviceNum, int maxTicksBack) throws IllegalArgumentException {
        checkTicksBack(maxTicksBack);
        int iters = getNumTicksBack(maxTicksBack);
        ListIterator<VRPose> iterator = this.dataQueue.listIterator(this.dataQueue.size() - 1);
        Vec3 avg = getPart(this.dataQueue.getLast(), deviceNum).getPos();
        int i = iters;
        while (i > 0) {
            avg = avg.add(getPart(iterator.previous(), deviceNum).getPos());
            i--;
        }
        return avg.scale(1d / (iters + 1));
    }

    private void checkTicksBack(int ticksBack) {
        if (ticksBack < 0 || ticksBack > VRPoseHistory.MAX_TICKS_BACK) {
            throw new IllegalArgumentException("Value must be between 0 and " + VRPoseHistory.MAX_TICKS_BACK + ".");
        }
    }

    private VRPose getOldPose(int maxTicksBack) {
        if (this.dataQueue.size() <= maxTicksBack) {
            return this.dataQueue.getFirst();
        } else {
            return this.dataQueue.get(this.dataQueue.size() - maxTicksBack - 1);
        }
    }

    private VRBodyPart getPart(VRPose pose, int deviceNum) {
        return switch (deviceNum) {
            case -1 -> pose.getHMD();
            case 0, 1 -> pose.getController(deviceNum);
            default -> throw new IllegalStateException("Invalid deviceNum: " + deviceNum);
        };
    }

    private int getNumTicksBack(int maxTicksBack) {
        if (this.dataQueue.size() <= maxTicksBack) {
            return this.dataQueue.size() - 1;
        } else {
            return maxTicksBack;
        }
    }
}
