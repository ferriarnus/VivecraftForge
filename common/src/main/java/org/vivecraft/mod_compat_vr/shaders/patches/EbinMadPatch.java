package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches:
 * vec4(projMAD(gl_ProjectionMatrix, viewSpacePosition), viewSpacePosition.z * gl_ProjectionMatrix[2].w);
 */
public class EbinMadPatch extends Patch {
    public EbinMadPatch() {
        this.pattern = Pattern.compile("vec4\\s*\\(\\s*(\\w+)\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*\\)\\s*,\\s*\\3\\s*\\.\\s*[zbp]\\s*\\*\\s*\\2\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[waq]\\s*\\)", Pattern.CASE_INSENSITIVE);

        this.replacement = "$2 * vec4($3, 1.0)";
    }
}
