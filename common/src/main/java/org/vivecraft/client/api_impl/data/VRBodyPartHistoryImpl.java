package org.vivecraft.client.api_impl.data;

import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.client.data.VRBodyPartHistory;
import org.vivecraft.api.data.VRBodyPart;

import java.util.*;

public class VRBodyPartHistoryImpl implements VRBodyPartHistory {

    private final LinkedList<VRBodyPart> dataQueue = new LinkedList<>();

    public VRBodyPartHistoryImpl() {
    }

    public void addPose(VRBodyPart pose) {
        this.dataQueue.addFirst(pose);
        if (this.dataQueue.size() > VRBodyPartHistory.MAX_TICKS_BACK) {
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
    public List<VRBodyPart> getAllHistoricalData() {
        return new ArrayList<>(this.dataQueue);
    }

    @Override
    public VRBodyPart getHistoricalData(int ticksBack) throws IllegalArgumentException, IllegalStateException {
        checkTicksBack(ticksBack);
        if (this.dataQueue.size() <= ticksBack) {
            throw new IllegalStateException("Cannot retrieve data from " + ticksBack + " ticks ago, when there is " +
                "only data for up to " + (this.dataQueue.size() - 1) + " ticks ago.");
        }
        return this.dataQueue.get(ticksBack);
    }

    @Override
    public Vec3 netMovement(int maxTicksBack) throws IllegalArgumentException {
        checkTicksBack(maxTicksBack);
        Vec3 current = this.dataQueue.getLast().getPos();
        Vec3 old = getOldPose(maxTicksBack).getPos();
        return current.subtract(old);
    }

    @Override
    public Vec3 averageVelocity(int maxTicksBack) throws IllegalArgumentException {
        checkTicksBack(maxTicksBack);
        Vec3 current = this.dataQueue.getLast().getPos();
        Vec3 old = getOldPose(maxTicksBack).getPos();
        return current.subtract(old).scale(1d / getNumTicksBack(maxTicksBack));
    }

    @Override
    public Vec3 averagePosition(int maxTicksBack) throws IllegalArgumentException {
        checkTicksBack(maxTicksBack);
        int iters = getNumTicksBack(maxTicksBack);
        ListIterator<VRBodyPart> iterator = this.dataQueue.listIterator(this.dataQueue.size() - 1);
        Vec3 avg = this.dataQueue.getLast().getPos();
        int i = iters;
        while (i > 0) {
            avg = avg.add(iterator.previous().getPos());
            i--;
        }
        return avg.scale(1d / (iters + 1));
    }

    private void checkTicksBack(int ticksBack) {
        if (ticksBack < 0 || ticksBack > VRBodyPartHistory.MAX_TICKS_BACK) {
            throw new IllegalArgumentException("Value must be between 0 and " + VRBodyPartHistory.MAX_TICKS_BACK + ".");
        }
    }

    private VRBodyPart getOldPose(int maxTicksBack) {
        if (this.dataQueue.size() <= maxTicksBack) {
            return this.dataQueue.getFirst();
        } else {
            return this.dataQueue.get(this.dataQueue.size() - maxTicksBack - 1);
        }
    }

    /**
     * Converts maxTicksBack to the actual maximum number of ticks we can go back.
     *
     * @param maxTicksBack The maximum number of ticks to attempt to go back.
     * @return The actual number of ticks to go back by.
     */
    private int getNumTicksBack(int maxTicksBack) {
        if (this.dataQueue.size() <= maxTicksBack) {
            return this.dataQueue.size() - 1;
        } else {
            return maxTicksBack;
        }
    }
}
