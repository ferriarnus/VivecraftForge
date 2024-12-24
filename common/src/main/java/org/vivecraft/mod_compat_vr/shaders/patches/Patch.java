package org.vivecraft.mod_compat_vr.shaders.patches;

import java.util.regex.Pattern;

/**
 * holder of a regex pattern and a replacement string to patch shaders
 */
public abstract class Patch {

    protected Pattern pattern;
    protected String replacement;

    /**
     * @return the pattern to detect the affected code
     */
    public Pattern getPattern() {
        return this.pattern;
    }

    /**
     * @return the replacement string to fix code found with the pattern of this Patch
     */
    public String getReplacement() {
        return this.replacement;
    }
}
