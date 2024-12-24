package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * return projMAD(gbufferProjectionInverse, screenPos) / (screenPos.z * gbufferProjectionInverse[2].w + gbufferProjectionInverse[3].w);
 */
public class EbinProjInvPatch extends Patch {
    public EbinProjInvPatch() {
        this.pattern = Pattern.compile("return\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*,\\s*(\\w+)\\s*\\)\\s*/\\s*\\(\\s*\\3\\s*\\.\\s*[zbp]\\s*\\*\\s*\\2\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[waq]\\s*\\+\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[waq]\\s*\\)\\s*;", Pattern.CASE_INSENSITIVE);

        this.replacement = "vec4 vivecraft_$3 = $2 * vec4($3, 1.0);\nreturn vivecraft_$3.xyz / vivecraft_$3.w;";
    }
}
