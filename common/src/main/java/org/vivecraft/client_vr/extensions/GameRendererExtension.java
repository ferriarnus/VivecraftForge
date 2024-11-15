package org.vivecraft.client_vr.extensions;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public interface GameRendererExtension {

    /**
     * @return if the camera is in water
     */
    boolean vivecraft$isInWater();

    /**
     * @return if the camera in a block
     */
    float vivecraft$isInBlock();

    /**
     * set the player position/rotation to the current VR pass location
     */
    void vivecraft$setupRVE();

    /**
     * cache the player position/rotation to be able to restore it later
     */
    void vivecraft$cacheRVEPos(LivingEntity entity);

    /**
     * restore the cached player position/rotation
     */
    void vivecraft$restoreRVEPos(LivingEntity entity);

    /**
     * @return the cached original player y position
     */
    double vivecraft$getRveY();

    /**
     * @param partialTick partial Tick to interpolate the position
     * @return the cached original player position
     */
    Vec3 vivecraft$getRvePos(float partialTick);

    /**
     * @return the point the player is pointing at
     */
    Vec3 vivecraft$getCrossVec();

    /**
     * resets the projection matrix
     * @param partialTick partial ticks to interpolate fov changes
     */
    void vivecraft$resetProjectionMatrix(float partialTick);

    /**
     * @return the projection matrix of the third person pass, only valid if that pass was rendered before
     */
    Matrix4f vivecraft$getThirdPassProjectionMatrix();

    /**
     * @return min clipping plane
     */
    float vivecraft$getMinClipDistance();

    /**
     * sets if the GamerRenderer should run through the screen rendering part of {@code GameRenderer.render()}
     * @param shouldDrawScreen if screen rendering should happen
     */
    void vivecraft$setShouldDrawScreen(boolean shouldDrawScreen);

    /**
     * sets if the GamerRenderer should run through the gui rendering part of {@code GameRenderer.render()} <br>
     * if {@code shouldDrawScreen} is false, this part isn't reached
     * @param shouldDrawGui if gui rendering should happen
     */
    void vivecraft$setShouldDrawGui(boolean shouldDrawGui);
}
