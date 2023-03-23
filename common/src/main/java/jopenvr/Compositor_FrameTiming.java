package jopenvr;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class Compositor_FrameTiming extends Structure
{
    public int m_nSize;
    public int m_nFrameIndex;
    public int m_nNumFramePresents;
    public int m_nNumMisPresented;
    public int m_nNumDroppedFrames;
    public int m_nReprojectionFlags;
    public double m_flSystemTimeInSeconds;
    public float m_flPreSubmitGpuMs;
    public float m_flPostSubmitGpuMs;
    public float m_flTotalRenderGpuMs;
    public float m_flCompositorRenderGpuMs;
    public float m_flCompositorRenderCpuMs;
    public float m_flCompositorIdleCpuMs;
    public float m_flClientFrameIntervalMs;
    public float m_flPresentCallCpuMs;
    public float m_flWaitForPresentCpuMs;
    public float m_flSubmitFrameMs;
    public float m_flWaitGetPosesCalledMs;
    public float m_flNewPosesReadyMs;
    public float m_flNewFrameReadyMs;
    public float m_flCompositorUpdateStartMs;
    public float m_flCompositorUpdateEndMs;
    public float m_flCompositorRenderStartMs;
    public TrackedDevicePose_t m_HmdPose;
    public int m_nNumVSyncsReadyForUse;
    public int m_nNumVSyncsToFirstView;

    public Compositor_FrameTiming()
    {
    }

    protected List<String> getFieldOrder()
    {
        return Arrays.asList("m_nSize", "m_nFrameIndex", "m_nNumFramePresents", "m_nNumMisPresented", "m_nNumDroppedFrames", "m_nReprojectionFlags", "m_flSystemTimeInSeconds", "m_flPreSubmitGpuMs", "m_flPostSubmitGpuMs", "m_flTotalRenderGpuMs", "m_flCompositorRenderGpuMs", "m_flCompositorRenderCpuMs", "m_flCompositorIdleCpuMs", "m_flClientFrameIntervalMs", "m_flPresentCallCpuMs", "m_flWaitForPresentCpuMs", "m_flSubmitFrameMs", "m_flWaitGetPosesCalledMs", "m_flNewPosesReadyMs", "m_flNewFrameReadyMs", "m_flCompositorUpdateStartMs", "m_flCompositorUpdateEndMs", "m_flCompositorRenderStartMs", "m_HmdPose", "m_nNumVSyncsReadyForUse", "m_nNumVSyncsToFirstView");
    }

    public Compositor_FrameTiming(Pointer peer)
    {
        super(peer);
    }

    public static class ByReference extends Compositor_FrameTiming implements Structure.ByReference
    {
    }

    public static class ByValue extends Compositor_FrameTiming implements Structure.ByValue
    {
    }
}
