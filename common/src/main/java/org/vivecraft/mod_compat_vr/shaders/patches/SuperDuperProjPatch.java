package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * vec2 clipCoord = vec2(projection[0].x, projection[1].y) * viewPos.xy;
 * return 0.5 - vec3(clipCoord.xy / viewPos.z, projection[3].z / viewPos.z + projection[2].z) * 0.5;
 */
public class SuperDuperProjPatch extends Patch {
    public SuperDuperProjPatch() {
        this.pattern = Pattern.compile(
            "vec2\\s+(\\w+)\\s*=\\s*vec2\\s*\\(\\s*(\\w+)\\s*\\[\\s*0\\s*\\]\\s*\\.\\s*[xrs]\\s*,\\s*\\2\\s*\\[\\s*1\\s*\\]\\s*\\.\\s*[ygt]\\s*\\)\\s*\\*\\s*(\\w+)\\s*\\.\\s*[xrs][ygt]\\s*;\\s*return\\s+0\\.5\\s*-\\s*vec3\\s*\\(\\s*\\1\\s*\\.\\s*[xrs][ygt]\\s*/\\s*\\3\\s*\\.\\s*[zbp]\\s*,\\s*\\2\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[zbp]\\s*/\\s*\\3\\s*\\.\\s*[zbp]\\s*\\+\\s*\\2\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[zbp]\\s*\\)\\s*(\\*\\s*0\\.5|/\\s*(2|2\\.|2\\.0))\\s*;",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "vec4 $1 = $2 * vec4($3, 1);\nreturn ($1.xyz / $1.w) * 0.5 + 0.5;";
    }
}
