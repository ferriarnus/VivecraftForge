package org.vivecraft.api;

import net.minecraft.world.entity.player.Player;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.common.api_impl.VRAPIImpl;

import javax.annotation.Nullable;

/**
 * The main interface for interacting with Vivecraft from common code.
 */
public interface VRAPI {

    /**
     * @return The Vivecraft API instance for interacting with Vivecraft's API.
     */
    static VRAPI getInstance() {
        return VRAPIImpl.INSTANCE;
    }

    /**
     * Check whether a given player is currently in VR.
     *
     * @param player The player to check the VR status of.
     * @return true if the player is in VR.
     */
    boolean isVRPlayer(Player player);

    /**
     * Returns the VR pose for the given player. Will return null if the player isn't in VR,
     * or if being called from the client and the client has yet to receive any data for the player.
     *
     * @param player Player to get the VR pose of.
     * @return The VR pose for a player, or null if the player isn't in VR or no data has been received for said player.
     */
    @Nullable
    VRPose getVRPose(Player player);
}
