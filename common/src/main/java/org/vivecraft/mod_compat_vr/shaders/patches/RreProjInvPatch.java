package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * vec3 viewpos    = vec3(vec2(projInv[0].x, projInv[1].y)*screenpos.xy + projInv[3].xy, projInv[3].z);
 * viewpos    /= projInv[2].w*screenpos.z + projInv[3].w;
 */
public class RreProjInvPatch extends Patch {
    public RreProjInvPatch() {
        this.pattern = Pattern.compile(
            "vec3\\s*(\\w+)\\s*=\\s*vec3\\s*\\(\\s*vec2\\s*\\(\\s*(\\w+)\\s*\\[\\s*0\\s*\\]\\s*\\.\\s*[xrs]\\s*,\\s*\\2\\s*\\[\\s*1\\s*\\]\\s*\\.\\s*[ygt]\\s*\\)\\s*\\*\\s*(\\w+)\\s*\\.\\s*[xrs][ygt]\\s*\\+\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.[xrs][ygt]\\s*,\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[zbp]\\s*\\)\\s*;\\s*\\1\\s*/=\\s*\\2\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[waq]\\s*\\*\\s*\\3\\s*\\.\\s*[zbp]\\s*\\+\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[waq]\\s*;",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "vec4 vivecraft_$1 = $2 * vec4($3.xyz, 1.0);\nvec3 $1 = vivecraft_$1.xyz / vivecraft_$1.w;";
    }
}
