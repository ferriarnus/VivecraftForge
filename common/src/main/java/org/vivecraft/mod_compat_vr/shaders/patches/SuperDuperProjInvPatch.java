package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * vec3 viewPos = vec3(vec2(projectionInverse[0].x, projectionInverse[1].y) * (screenPos.xy * 2.0 - 1.0), -1);
 * return viewPos / (projectionInverse[2].w * (screenPos.z * 2.0 - 1.0) + projectionInverse[3].w);
 */
public class SuperDuperProjInvPatch extends Patch {
    public SuperDuperProjInvPatch() {
        this.pattern = Pattern.compile(
            "vec3\\s+(\\w+)\\s*=\\s*vec3\\s*\\(\\s*vec2\\s*\\(\\s*(\\w+)\\s*\\[\\s*0\\s*\\]\\s*\\.\\s*[xrs]\\s*,\\s*\\2\\s*\\[\\s*1\\s*\\]\\s*\\.\\s*[ygt]\\s*\\)\\s*\\*\\s*\\(\\s*(\\w+)\\s*\\.\\s*[xrs][ygt]\\s*\\*\\s*(2|2\\.|2\\.0)\\s*-\\s*(1|1\\.|1\\.0)\\s*\\)\\s*,\\s*-\\s*(1|1\\.|1\\.0)\\s*\\)\\s*;\\s*return\\s+\\1\\s*/\\s*\\(\\s*\\2\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[waq]\\s*\\*\\s*\\(\\s*\\3\\s*\\.\\s*[zbp]\\s*\\*\\s*(2|2\\.|2\\.0)\\s*-\\s*(1|1\\.|1\\.0)\\s*\\)\\s*\\+\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[waq]\\s*\\)\\s*;",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "vec4 $1 = $2 * vec4($3 * 2.0 - 1.0, 1.0);\nreturn $1.xyz / $1.w;";
    }
}
