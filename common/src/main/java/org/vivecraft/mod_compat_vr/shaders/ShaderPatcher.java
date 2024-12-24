package org.vivecraft.mod_compat_vr.shaders;

import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.mod_compat_vr.shaders.patches.*;

import java.util.Set;

public class ShaderPatcher {

    private static final Set<Patch> PATCHES = Set.of(
        //new ProjMadPatch(),
        new ProjDiag4ZPatch(),
        new SuperDuperMadPatch(),
        new ProjDiag4Patch(),
        new EbinMadPatch(),
        new RreProjInvPatch(),
        new SuperDuperProjInvPatch(),
        new EbinProjInvPatch(),
        new SuperDuperProjPatch(),
        new EbinProjPatch(),
        new ProjDiag3Patch()
    );

    /**
     * patches known incompatibilities with VR
     *
     * @param shader shader code to patch
     * @return patched shader code
     */
    public static String patchShader(String shader) {
        if (ClientDataHolderVR.getInstance().vrSettings.shaderPatching) {
            for (Patch patch : PATCHES) {
                shader = patch.getPattern().matcher(shader).replaceAll(patch.getReplacement());
            }
        }
        return shader;
    }
}
