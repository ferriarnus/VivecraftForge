package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * pos     = pos.xyzz * diag4(gl_ProjectionMatrix) + vec4(0.0, 0.0, gl_ProjectionMatrix[3].z, 0.0);
 */
public class ProjDiag4ZPatch extends Patch {
    public ProjDiag4ZPatch() {
        this.pattern = Pattern.compile(
            "(\\w+)\\s*=\\s*\\1\\s*\\.\\s*(xyzz|rgbb|stpp)\\s*\\*\\s*(diag4|diagonal4)\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\+\\s*vec4\\s*\\(\\s*(0\\.0|0\\.|0)\\s*,\\s*(0\\.0|0\\.|0)\\s*,\\s*\\4\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[zbp]\\s*,\\s*(0\\.0|0\\.|0)\\s*\\)\\s*;",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "$1 = $4 * vec4($1.xyz, 1.0);";
    }
}
