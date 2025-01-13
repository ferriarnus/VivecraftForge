package org.vivecraft.api.client.data;

import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.data.VRBodyPart;

import java.util.List;

/**
 * Represents historical {@link VRBodyPart}s associated with a player.
 */
public interface VRBodyPartHistory {

    /**
     * The maximum amount of ticks back data is held for.
     * It is only guaranteed that historical data does not go beyond this number of ticks back. Functions do not
     * guarantee that they will reference this many ticks, as, for example, this amount of ticks may not have gone
     * by for the player this history represents.
     * Passing a value larger than this number to any methods below in their maxTicksBack or ticksBack parameters
     * will throw an {@link IllegalArgumentException}.
     */
    int MAX_TICKS_BACK = 20;

    /**
     * @return The amount of ticks worth of history being held. The number returned by this methodwill never be higher
     * than {@link VRBodyPartHistory#MAX_TICKS_BACK}, however can be lower than it.
     */
    int ticksOfHistory();

    /**
     * Gets a raw list of {@link VRBodyPart} instances, with index 0 representing the least recent pose known.
     *
     * @return The aforementioned list of {@link VRBodyPart} instances.
     */
    List<VRBodyPart> getAllHistoricalData() throws IllegalArgumentException;

    /**
     * Gets the historical data ticksBack ticks back. This will throw an IllegalStateException if the data cannot
     * be retrieved due to not having enough history.
     *
     * @param ticksBack Ticks back to retrieve data.
     * @return A {@link VRBodyPart} instance from index ticks ago.
     * @throws IllegalStateException    If ticksBack references a tick that there is not yet data for.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    VRBodyPart getHistoricalData(int ticksBack) throws IllegalArgumentException, IllegalStateException;

    /**
     * Gets the net movement between the most recent data in this instance and the oldest position that can be
     * retrieved, going no farther back than maxTicksBack.
     *
     * @param maxTicksBack The maximum amount of ticks back to compare the most recent data with.
     * @return The aforementioned net movement. Note that this will return zero change on all axes if only zero ticks
     * can be looked back.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    Vec3 netMovement(int maxTicksBack) throws IllegalArgumentException;

    /**
     * Gets the average velocity in blocks/tick between the most recent data in this instance and the oldest position
     * that can be retrieved, going no farther back than maxTicksBack.
     *
     * @param maxTicksBack The maximum amount of ticks back to calculate velocity with.
     * @return The aforementioned average velocity on each axis. Note that this will return zero velocity on all axes
     * if only zero ticks can be looked back.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    Vec3 averageVelocity(int maxTicksBack) throws IllegalArgumentException;

    /**
     * Gets the average speed in blocks/tick between the most recent data in this instance and the oldest position
     * that can be retrieved, going no farther back than maxTicksBack.
     *
     * @param maxTicksBack The maximum amount of ticks back to calculate speed with.
     * @return The aforementioned average speed on each axis. Note that this will return zero speed if only zero ticks
     * can be looked back.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    default double averageSpeed(int maxTicksBack) throws IllegalArgumentException {
        Vec3 averageVelocity = averageVelocity(maxTicksBack);
        return Math.sqrt(averageVelocity.x() * averageVelocity.x() +
            averageVelocity.y() * averageVelocity.y() +
            averageVelocity.z() * averageVelocity.z());
    }

    /**
     * Gets the average position between the most recent data in this instance and the oldest position that can be
     * retrieved, going no farther back than maxTicksBack.
     *
     * @param maxTicksBack The maximum amount of ticks back to calculate velocity with.
     * @return The aforementioned average position. Note that this will return the current position if only zero ticks
     * can be looked back.
     * @throws IllegalArgumentException Thrown when maxTicksBack is larger than {@value #MAX_TICKS_BACK} or less than 0.
     */
    Vec3 averagePosition(int maxTicksBack) throws IllegalArgumentException;
}
