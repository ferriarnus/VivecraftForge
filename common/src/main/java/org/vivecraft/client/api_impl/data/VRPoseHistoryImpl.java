package org.vivecraft.client.api_impl.data;

import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.client.data.VRPoseHistory;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
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
        return current.subtract(old).scale(1d / getNumTicksBack(maxTicksBack));
    }

    @Override
    public double averageSpeed(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        Vec3 averageVelocity = averageVelocity(bodyPart, maxTicksBack);
        if (averageVelocity == null) {
            return 0;
        }
        return Math.sqrt(averageVelocity.x() * averageVelocity.x() +
                averageVelocity.y() * averageVelocity.y() +
                averageVelocity.z() * averageVelocity.z());
    }

    @Override
    public Vec3 averagePosition(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        checkPartNonNull(bodyPart);
        checkTicksBack(maxTicksBack);
        int iters = getNumTicksBack(maxTicksBack);
        VRBodyPartData currentData = this.dataQueue.getLast().getBodyPartData(bodyPart);
        if (currentData == null) {
            return null;
        }
        ListIterator<VRPose> iterator = this.dataQueue.listIterator(this.dataQueue.size() - 1);
        Vec3 avg = currentData.getPos();
        int i = iters;
        while (i > 0) {
            VRBodyPartData oldData = iterator.previous().getBodyPartData(bodyPart);
            if (oldData == null) {
                break;
            }
            avg = avg.add(oldData.getPos());
            i--;
        }
        return avg.scale(1d / (iters + 1));
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
