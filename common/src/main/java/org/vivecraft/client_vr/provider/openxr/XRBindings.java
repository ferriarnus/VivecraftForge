package org.vivecraft.client_vr.provider.openxr;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;

public class XRBindings {

    public static HashSet<String> supportedHeadsets() {
        HashSet<String> set = new HashSet<>();
        if (MCOpenXR.get().systemName.toLowerCase().contains("oculus") || MCOpenXR.get().systemName.toLowerCase().contains("meta")) {
            set.add("/interaction_profiles/oculus/touch_controller");
            return set;
        }
        if (MCOpenXR.get().session.getCapabilities().XR_HTC_vive_cosmos_controller_interaction) {
            set.add("/interaction_profiles/htc/vive_cosmos_controller");
        }
        set.add("/interaction_profiles/htc/vive_controller");
        return set;
    }

    private static HashSet<Pair<String, String>> quest2Bindings() {
        HashSet<Pair<String, String>> set = new HashSet<>();

        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/y/click"));
        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.toggleKeyboard", "/user/hand/left/input/squeeze"));
        set.add(new MutablePair<>("/actions/global/in/key.inventory", "/user/hand/left/input/x/click"));

        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/squeeze"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/squeeze"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiRightClick", "/user/hand/right/input/a/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/thumbstick/y"));

        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarPrev", "/user/hand/left/input/squeeze"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/squeeze"));
        set.add(new MutablePair<>("/actions/ingame/in/key.attack", "/user/hand/right/input/trigger"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleport", "/user/hand/left/input/trigger"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.radialMenu", "/user/hand/right/input/b/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.use", "/user/hand/right/input/a/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleportFallback", "/user/hand/left/input/trigger/value"));
        set.add(new MutablePair<>("/actions/ingame/in/key.jump", "/user/hand/left/input/thumbstick"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.freeMoveStrafe", "/user/hand/left/input/thumbstick"));
        set.add(new MutablePair<>("/actions/ingame/in/key.sneak", "/user/hand/right/input/thumbstick"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.rotateAxis", "/user/hand/right/input/thumbstick"));

        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/squeeze"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/right/input/squeeze"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger"));

        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/squeeze"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/squeeze"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/trigger"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/trigger"));

        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/squeeze"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/squeeze"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/trigger"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/trigger"));
        return set;
    }

    private static HashSet<Pair<String, String>> viveBindings() {
        HashSet<Pair<String, String>> set = new HashSet<>();

        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/menu/click"));
        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.toggleKeyboard", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/global/in/key.inventory", "/user/hand/right/input/trackpad/click"));

        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiRightClick", "/user/hand/right/input/trackpad/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/trackpad/y"));

        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarPrev", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.attack", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleport", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.radialMenu", "/user/hand/right/input/menu/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.use", "/user/hand/right/input/trackpad/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleportFallback", "/user/hand/left/input/trigger/value"));
        set.add(new MutablePair<>("/actions/ingame/in/key.jump", "/user/hand/left/input/trackpad/x"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.freeMoveStrafe", "/user/hand/left/input/trackpad"));
        set.add(new MutablePair<>("/actions/ingame/in/key.sneak", "/user/hand/left/input/trackpad/y"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.rotateAxis", "/user/hand/right/input/trackpad"));

        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger/click"));

        set.add(new MutablePair<>("/actions/technical/in/vivecraft.key.trackpadTouch", "/user/hand/left/input/trackpad/click"));
        set.add(new MutablePair<>("/actions/technical/in/vivecraft.key.trackpadTouch", "/user/hand/right/input/trackpad/touch"));

        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/trigger/click"));

        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/trigger/click"));

        return set;
    }

    private static HashSet<Pair<String, String>> cosmosBindings() {
        HashSet<Pair<String, String>> set = new HashSet<>();

        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.ingameMenuButton", "/user/hand/left/input/y/click"));
        set.add(new MutablePair<>("/actions/global/in/vivecraft.key.toggleKeyboard", "/user/hand/left/input/y/click"));
        set.add(new MutablePair<>("/actions/global/in/key.inventory", "/user/hand/left/input/x/click"));

        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiShift", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiMiddleClick", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiLeftClick", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiRightClick", "/user/hand/right/input/a/click"));
        set.add(new MutablePair<>("/actions/gui/in/vivecraft.key.guiScrollAxis", "/user/hand/right/input/thumbstick"));

        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarPrev", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.hotbarNext", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.attack", "/user/hand/right/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleport", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.radialMenu", "/user/hand/right/input/b/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.use", "/user/hand/right/input/a/click"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.freeMoveStrafe", "/user/hand/left/input/thumbstick"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.rotateAxis", "/user/hand/right/input/thumbstick"));
        set.add(new MutablePair<>("/actions/ingame/in/vivecraft.key.teleportFallback", "/user/hand/left/input/trigger/value"));
        set.add(new MutablePair<>("/actions/ingame/in/key.jump", "/user/hand/left/input/shoulder/click"));
        set.add(new MutablePair<>("/actions/ingame/in/key.sneak", "/user/hand/right/input/shoulder/click"));

        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardShift", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/keyboard/in/vivecraft.key.keyboardClick", "/user/hand/right/input/trigger/click"));

        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.vrInteract", "/user/hand/right/input/trigger/click"));

        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/squeeze/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/left/input/trigger/click"));
        set.add(new MutablePair<>("/actions/contextual/in/vivecraft.key.climbeyGrab", "/user/hand/right/input/trigger/click"));
        return set;
    }

    public static HashSet<Pair<String, String>> getBinding(String Headset){
        switch (Headset) {
            case "/interaction_profiles/htc/vive_cosmos_controller" -> {
                return cosmosBindings();
            }
            case "/interaction_profiles/htc/vive_controller" -> {
                return viveBindings();
            }
            case "/interaction_profiles/oculus/touch_controller" -> {
                return quest2Bindings();
            }
            default -> {
                return viveBindings();
            }
        }
    }
}
