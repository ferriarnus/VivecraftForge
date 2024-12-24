package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * patches
 * gl_Position.xyz = getMatScale(mat3(gl_ProjectionMatrix)) * vertexViewPos;
 * gl_Position.z += gl_ProjectionMatrix[3].z;
 * gl_Position.w = -vertexViewPos.z;
 */
public class SuperDuperMadPatch extends Patch {
    public SuperDuperMadPatch() {
        this.pattern = Pattern.compile(
            "(\\w+)\\s*\\.\\s*[xrs][ygt][zbp]\\s*=\\s*(\\w+)\\s*\\(\\s*mat3\\s*\\(\\s*(\\w+)\\s*\\)\\s*\\)\\s*\\*\\s*(\\w+)\\s*;\\s*\\1\\s*\\.\\s*[zbp]\\s*\\+=\\s*\\3\\s*\\[\\s*3\\s*\\]\\s*\\.\\s*[zbp]\\s*;\\s*\\1\\s*\\.\\s*[waq]\\s*=\\s*-\\s*\\4\\s*\\.\\s*[zbp]\\s*;",
            Pattern.CASE_INSENSITIVE);

        this.replacement = "$1 = $3 * vec4($4, 1.0);";
    }
}
