package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches all of these
 * diagonal4(mat) * v.xyzz + mat[3];
 * v.xyzz * diagonal4(mat) + mat[3];
 * vec4(m[0].x, m[1].y, m[2].zw) * pos.xyzz + m[3];
 * iProjDiag * p3.xyzz + gbufferProjectionInverse[3];
 */
public class ProjDiag4Patch extends Patch {
    public ProjDiag4Patch() {
        this.pattern = Pattern.compile(
            "((((diagonal4|diag4)\\(\\w+\\))|\\w+|vec4\\s*\\(\\s*(\\w+)\\s*\\[\\s*0\\s*\\]\\s*\\.\\s*[xrs]\\s*,\\s*\\5\\s*\\[\\s*1\\s*\\]\\s*\\.\\s*[ygt]\\s*,\\s*\\5\\s*\\[\\s*2\\s*\\]\\s*\\.\\s*[zbp][waq]\\s*\\))\\s*\\*\\s*)?(\\w+)\\s*\\.\\s*[xrs][ygt][zbp][zbp](\\s*\\*\\s*((diagonal4|diag4)\\(\\w+\\)))?\\s*\\+\\s*(\\w+)\\s*\\[\\s*3\\s*\\]\\s*;",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "$10 * vec4($6, 1.0);";
    }
}
