package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches all of these
 * diagonal3(m) * (v) + (m)[3].xyz
 * diagonal3(mat) * v + mat[3].xyz
 * vec3(projection[0].x, projection[1].y, projection[2].z) * viewPosition + projection[3].xyz
 */
public class ProjDiag3Patch extends Patch {
    public ProjDiag3Patch() {
        this.pattern = Pattern.compile(
            "(diagonal3|diag3|vec3)\\s*\\(\\s*(\\w+)\\s*(\\[\\s*0\\s*\\]\\s*\\.\\s*[xrs]\\s*,\\s*\\2\\s*\\[\\s*1\\s*\\]\\s*\\.\\s*[ygt]\\s*,\\s*\\2\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[zbp])?\\s*\\)\\s*\\*\\s*\\(?(\\w+)\\)?\\s*\\+\\s*\\(?\\2\\)?\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[xrs][ygt][zbp]",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "($2 * vec4($4, 1.0)).xyz";
    }
}
