package org.vivecraft.client.extensions;

import org.vivecraft.client.render.VRPlayerRenderer;

import java.util.Map;

public interface EntityRenderDispatcherExtension {
    /**
     * @return map of VR player renderers with the vanilla model
     */
    Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRVanilla();

    /**
     * @return map of VR player renderers with split arms
     */
    Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRArms();

    /**
     * @return map of VR player renderers with split arms and legs
     */
    Map<String, VRPlayerRenderer> vivecraft$getSkinMapVRLegs();
}
