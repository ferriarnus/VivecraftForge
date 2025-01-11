package org.vivecraft.client_vr.provider.control;

import net.minecraft.client.KeyMapping;
import org.vivecraft.client.VivecraftVRMod;

public enum VRInputActionSet {
    INGAME("/actions/ingame", "vivecraft.actionset.ingame", "leftright", false),
    GUI("/actions/gui", "vivecraft.actionset.gui", "leftright", false),
    GLOBAL("/actions/global", "vivecraft.actionset.global", "leftright", false),
    MOD("/actions/mod", "vivecraft.actionset.mod", "leftright", false),
    CONTEXTUAL("/actions/contextual", "vivecraft.actionset.contextual", "single", false),
    KEYBOARD("/actions/keyboard", "vivecraft.actionset.keyboard", "single", true),
    MIXED_REALITY("/actions/mixedreality", "vivecraft.actionset.mixedReality", "single", true),
    TECHNICAL("/actions/technical", "vivecraft.actionset.technical", "leftright", true);

    /**
     * ActionSet path for the VR runtime
     */
    public final String name;
    /**
     * translation key for the human-readable name
     */
    public final String localizedName;
    /**
     * one off: <br>
     * leftright: set has separate bindings for left and right hand <br>
     * single: left and right hand have the same bindings <br>
     * hidden: This action set will not be shown to the user
     */
    public final String usage;

    /**
     * advanced action sets are hidden by default
     */
    public final boolean advanced;

    VRInputActionSet(String name, String localizedName, String usage, boolean advanced) {
        this.name = name;
        this.localizedName = localizedName;
        this.usage = usage;
        this.advanced = advanced;
    }

    /**
     * converts the KeyMappings category to an Actionset
     *
     * @param keyBinding KeyMapping to get the ActionSet for
     * @return ActionSet the KeyMapping should  be put in
     */
    public static VRInputActionSet fromKeyBinding(KeyMapping keyBinding) {
        return switch (keyBinding.getCategory()) {
            case "vivecraft.key.category.gui" -> GUI;
            case "vivecraft.key.category.climbey" -> CONTEXTUAL;
            case "vivecraft.key.category.keyboard" -> KEYBOARD;
            default -> VivecraftVRMod.INSTANCE.isModBinding(keyBinding) ? MOD : INGAME;
        };
    }
}
