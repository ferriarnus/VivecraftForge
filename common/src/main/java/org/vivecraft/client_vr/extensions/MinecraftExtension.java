package org.vivecraft.client_vr.extensions;

public interface MinecraftExtension {

    /**
     * draws the profiler pie on the screen
     */
    void vivecraft$drawProfiler();

    /**
     * returns the partialTick when running and the pausePartialTick when paused
     */
    float vivecraft$getPartialTick();
}
