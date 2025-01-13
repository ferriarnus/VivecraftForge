package org.vivecraft.api.client.data;

import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRPose;

import javax.annotation.Nullable;
import java.util.List;

public interface VRPoseHistory {

    /**
     * The maximum amount of ticks back poses are held for.
     * It is only guaranteed that the pose history does not go beyond this number of ticks back. Functions do not
     * guarantee that they will reference this many ticks, as, for example, this amount of ticks may not have gone
     * by for the player this history represents.
     * Passing a value larger than this number to any methods below in their maxTicksBack or ticksBack parameters
     * will throw an {@link IllegalArgumentException}.
     */
    int MAX_TICKS_BACK = 20;

    /**
     * @return The amount of ticks worth of history being held. The number returned by this method will never be higher
     * than {@link VRPoseHistory#MAX_TICKS_BACK}, however can be lower than it.
     */
    int ticksOfHistory();

    /**
     * Gets a raw list of {@link VRPose} instances, with index 0 representing the least recent pose known.
     *
     * @return The aforementioned list of {@link VRPose} instances.
     */
    List<VRPose> getAllHistoricalData() throws IllegalArgumentException;

    /**
     * Gets the pose history {@code ticksBack} ticks back. This will throw an IllegalStateException if the data cannot
     * be retrieved due to not having enough history.
     *
     * @param ticksBack Ticks back to retrieve data.
     * @return A {@link VRPose} instance from index ticks ago.
     * @throws IllegalStateException If ticksBack references a tick that there is not yet data for.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    VRPose getHistoricalData(int ticksBack) throws IllegalArgumentException, IllegalStateException;

    /**
     * Gets the net movement between the most recent pose in this instance and the oldest position that can be
     * retrieved, going no farther back than {@code maxTicksBack}.
     *
     * @param bodyPart The body part to get the net movement for.
     * @param maxTicksBack The maximum amount of ticks back to compare the most recent data with.
     * @return The aforementioned net movement. Note that this will return zero change on all axes if only zero ticks
     * can be looked back. Will be null if the body part requested isn't available.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    @Nullable
    Vec3 netMovement(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException;

    /**
     * Gets the average velocity in blocks/tick between the most recent pose in this instance and the oldest position
     * that can be retrieved, going no farther back than {@code maxTicksBack}.
     *
     * @param bodyPart The body part to get the average velocity for.
     * @param maxTicksBack The maximum amount of ticks back to calculate velocity with.
     * @return The aforementioned average velocity on each axis. Note that this will return zero velocity on all axes
     * if only zero ticks can be looked back. Will be null if the body part requested isn't available.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    @Nullable
    Vec3 averageVelocity(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException;

    /**
     * Gets the average speed in blocks/tick between the most recent pose in this instance and the oldest position
     * that can be retrieved, going no farther back than {@code maxTicksBack}.
     *
     * @param bodyPart The body part to get the average speed for.
     * @param maxTicksBack The maximum amount of ticks back to calculate speed with.
     * @return The aforementioned average speed on each axis. Note that this will return zero speed if only zero ticks
     * can be looked back. Will be 0 if the body part requested isn't available.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    default double averageSpeed(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException {
        Vec3 averageVelocity = averageVelocity(bodyPart, maxTicksBack);
        if (averageVelocity == null) {
            return 0;
        }
        return Math.sqrt(averageVelocity.x() * averageVelocity.x() +
            averageVelocity.y() * averageVelocity.y() +
            averageVelocity.z() * averageVelocity.z());
    }

    /**
     * Gets the average position between the most recent pose in this instance and the oldest position that can be
     * retrieved, going no farther back than maxTicksBack.
     *
     * @param bodyPart The body part to get the average position for.
     * @param maxTicksBack The maximum amount of ticks back to calculate velocity with.
     * @return The aforementioned average position. Note that this will return the current position if only zero ticks
     * can be looked back. Will be null if the body part requested isn't available.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    @Nullable
    Vec3 averagePosition(VRBodyPart bodyPart, int maxTicksBack) throws IllegalArgumentException;

}
