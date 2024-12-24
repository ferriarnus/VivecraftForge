package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * return (diagonal2(gbufferProjection) * viewSpacePosition.xy + gbufferProjection[3].xy) / -viewSpacePosition.z * 0.5 + 0.5;
 */
public class EbinProjPatch extends Patch {
    public EbinProjPatch() {
        this.pattern = Pattern.compile("return\\s+\\(\\s*(diagonal2|diag2)\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\*\\s*(\\w+)\\s*\\.\\s*[xrs][ygt]\\s*\\+\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[xrs][ygt]\\s*\\)\\s*/\\s*-\\3\\s*\\.\\s*[zbp]\\s*\\*\\s*0\\.5\\s*\\+\\s*0\\.5;", Pattern.CASE_INSENSITIVE);

        this.replacement = "vec4 vivecraft_pos = $2 * vec4($3, 1.0);\nreturn (vivecraft_pos.xy / vivecraft_pos.w) * 0.5 + 0.5;";
    }
}
