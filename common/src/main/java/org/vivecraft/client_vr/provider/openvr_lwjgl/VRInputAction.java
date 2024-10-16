package org.vivecraft.client_vr.provider.openvr_lwjgl;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.HandedKeyBinding;
import org.vivecraft.client_vr.provider.InputSimulator;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.control.VRInputActionSet;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class VRInputAction {
    public final KeyMapping keyBinding;
    public final String name;
    public final String requirement;
    public final String type;
    public final VRInputActionSet actionSet;

    private int priority = 0;
    private final boolean[] enabled = new boolean[ControllerType.values().length];
    private final List<KeyListener> listeners = new ArrayList<>();
    private ControllerType currentHand = ControllerType.RIGHT;
    // Only used for the UseTracked axis methods
    private boolean currentlyInUse;

    public long handle;
    private final boolean[] pressed = new boolean[ControllerType.values().length];
    protected final int[] unpressInTicks = new int[ControllerType.values().length];

    public final DigitalData[] digitalData = new DigitalData[ControllerType.values().length];
    public final AnalogData[] analogData = new AnalogData[ControllerType.values().length];

    public VRInputAction(KeyMapping keyMapping, String requirement, String type, VRInputActionSet actionSetOverride) {
        this.keyBinding = keyMapping;
        this.requirement = requirement;
        this.type = type;
        this.actionSet = actionSetOverride != null ? actionSetOverride : VRInputActionSet.fromKeyBinding(keyMapping);
        this.name = this.actionSet.name + "/in/" + keyMapping.getName().replace('/', '_');

        for (int c = 0; c < ControllerType.values().length; c++) {
            this.enabled[c] = true;
            this.analogData[c] = new AnalogData();
            this.digitalData[c] = new DigitalData();
        }
    }

    public boolean isButtonPressed() {
        if (this.type.equals("boolean")) {
            return this.digitalData().state;
        } else {
            Vector3fc axis = this.getAxis3D(false);
            return Math.abs(axis.x()) > 0.5F || Math.abs(axis.y()) > 0.5F || Math.abs(axis.z()) > 0.5F;
        }
    }

    public boolean isButtonChanged() {
        if (this.type.equals("boolean")) {
            return this.digitalData().isChanged;
        } else {
            Vector3fc axis = this.getAxis3D(false);
            Vector3fc delta = this.getAxis3D(true);
            return Math.abs(axis.x() - delta.x()) > 0.5F != Math.abs(axis.x()) > 0.5F ||
                Math.abs(axis.y() - delta.y()) > 0.5F != Math.abs(axis.y()) > 0.5F ||
                Math.abs(axis.z() - delta.z()) > 0.5F != Math.abs(axis.z()) > 0.5F;
        }
    }

    public float getAxis1D(boolean delta) {
        return switch (this.type) {
            case "boolean" -> this.digitalToAnalog(delta);
            case "vector1", "vector2", "vector3" ->
                delta ? this.analogData().deltaX : this.analogData().x;
            default -> 0.0F;
        };
    }

    public Vector2fc getAxis2D(boolean delta) {
        return switch (this.type) {
            case "boolean" -> new Vector2f(this.digitalToAnalog(delta), 0.0F);
            case "vector1" ->
                delta ? new Vector2f(this.analogData().deltaX, 0.0F) : new Vector2f(this.analogData().x, 0.0F);
            case "vector2", "vector3" ->
                delta ? new Vector2f(this.analogData().deltaX, this.analogData().deltaY) :
                new Vector2f(this.analogData().x, this.analogData().y);
            default -> new Vector2f();
        };
    }

    public Vector3fc getAxis3D(boolean delta) {
        return switch (this.type) {
            case "boolean" -> new Vector3f(this.digitalToAnalog(delta), 0.0F, 0.0F);
            case "vector1" -> delta ? new Vector3f(this.analogData().deltaX, 0.0F, 0.0F) :
                new Vector3f(this.analogData().x, 0.0F, 0.0F);
            case "vector2" -> delta ? new Vector3f(this.analogData().deltaX, this.analogData().deltaY, 0.0F) :
                new Vector3f(this.analogData().x, this.analogData().y, 0.0F);
            case "vector3" ->
                delta ? new Vector3f(this.analogData().deltaX, this.analogData().deltaY, this.analogData().deltaZ) :
                    new Vector3f(this.analogData().x, this.analogData().y, this.analogData().z);
            default -> new Vector3f();
        };
    }

    /**
     * This special variant of getAxis1D internally handles the isEnabled check and will continue
     * to give an output even after disabled until the user lets go of the input.
     * Cannot provide delta values as it wouldn't make any sense.
     */
    public float getAxis1DUseTracked() {
        if (this.currentlyInUse || this.isEnabled()) {
            float axis = this.getAxis1D(false);
            this.currentlyInUse = axis != 0.0F;
            return axis;
        } else {
            return 0.0F;
        }
    }

    /**
     * This special variant of getAxis2D internally handles the isEnabled check and will continue
     * to give an output even after disabled until the user lets go of the input.
     * Cannot provide delta values as it wouldn't make any sense.
     */
    public Vector2fc getAxis2DUseTracked() {
        if (this.currentlyInUse || this.isEnabled()) {
            Vector2fc axis = this.getAxis2D(false);
            this.currentlyInUse = axis.x() != 0.0F || axis.y() != 0.0F;
            return axis;
        } else {
            return new Vector2f();
        }
    }

    /**
     * This special variant of getAxis3D internally handles the isEnabled check and will continue
     * to give an output even after disabled until the user lets go of the input.
     * Cannot provide delta values as it wouldn't make any sense.
     */
    public Vector3fc getAxis3DUseTracked() {
        if (this.currentlyInUse || this.isEnabled()) {
            Vector3fc axis = this.getAxis3D(false);
            this.currentlyInUse = axis.x() != 0.0F || axis.y() != 0.0F || axis.z() != 0.0F;
            return axis;
        } else {
            return new Vector3f();
        }
    }

    private float digitalToAnalog(boolean delta) {
        if (delta) {
            if (this.digitalData().isChanged) {
                return this.digitalData().state ? 1.0F : -1.0F;
            } else {
                return 0.0F;
            }
        } else {
            return this.digitalData().state ? 1.0F : 0.0F;
        }
    }

    public long getLastOrigin() {
        return switch (this.type) {
            case "boolean" -> this.digitalData().activeOrigin;
            case "vector1", "vector2", "vector3" -> this.analogData().activeOrigin;
            default -> 0L;
        };
    }

    public ControllerType getCurrentHand() {
        return this.currentHand;
    }

    public void setCurrentHand(ControllerType currentHand) {
        this.currentHand = currentHand;
    }

    private DigitalData digitalData() {
        return this.isHanded() ? this.digitalData[this.currentHand.ordinal()] : this.digitalData[0];
    }

    private AnalogData analogData() {
        return this.isHanded() ? this.analogData[this.currentHand.ordinal()] : this.analogData[0];
    }

    public void setHandle(long handle) {
        if (this.handle != 0L) {
            throw new IllegalStateException("Handle already assigned!");
        } else {
            this.handle = handle;
        }
    }

    public int getPriority() {
        return this.priority;
    }

    public VRInputAction setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * check if the InputAction is enabled, if it is handed, checks for {@link VRInputAction#currentHand} <br>
     * also checks if any other InputAction with higher priority is active, then this InputAction is treated as disabled
     */
    public boolean isEnabled() {
        if (!this.isEnabledRaw(this.currentHand)) return false;
        if (MCOpenVR.get() == null) return false;

        long lastOrigin = this.getLastOrigin();
        ControllerType hand = MCOpenVR.get().getOriginControllerType(lastOrigin);

        if (hand == null && this.isHanded()) return false;

        // iterate over all actions, and check if another action has a higher priority
        for (VRInputAction action : MCOpenVR.get().getInputActions()) {
            if (action != this && action.isEnabledRaw(hand) && action.isActive() &&
                action.getPriority() > this.getPriority() && MCVR.get().getOrigins(action).contains(lastOrigin))
            {
                if (action.isHanded()) {
                    return !((HandedKeyBinding) action.keyBinding).isPriorityOnController(hand);
                }
                return false;
            }
        }

        return true;
    }

    public boolean isEnabledRaw(ControllerType hand) {
        if (this.isHanded()) {
            return hand != null && this.enabled[hand.ordinal()];
        } else {
            return this.enabled[0];
        }
    }

    public boolean isEnabledRaw() {
        return Arrays.stream(ControllerType.values()).anyMatch(this::isEnabledRaw);
    }

    public VRInputAction setEnabled(ControllerType hand, boolean enabled) {
        if (!this.isHanded()) {
            throw new IllegalStateException("Not a handed key binding!");
        } else {
            this.enabled[hand.ordinal()] = enabled;
            return this;
        }
    }

    public VRInputAction setEnabled(boolean enabled) {
        if (this.isHanded()) {
            for (ControllerType controllertype : ControllerType.values()) {
                this.enabled[controllertype.ordinal()] = enabled;
            }
        } else {
            this.enabled[0] = enabled;
        }

        return this;
    }

    public boolean isActive() {
        return switch (this.type) {
            case "boolean" -> this.digitalData().isActive;
            case "vector1", "vector2", "vector3" -> this.analogData().isActive;
            default -> false;
        };
    }

    public boolean isHanded() {
        return this.keyBinding instanceof HandedKeyBinding;
    }

    /**
     * adds a KeyListener that gets notified for state changes
     * @param listener KeyListener to register
     */
    public void registerListener(KeyListener listener) {
        this.listeners.add(listener);
        this.listeners.sort(Comparator.comparingInt(KeyListener::getPriority).reversed());
    }

    /**
     * removes the specified KeyListeners
     */
    public void unregisterListener(KeyListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * notifies all registered KeyListener in priority order
     * @param pressed if presses or released
     * @param hand controller this was triggered by
     * @return if any KeyListener triggered
     */
    public boolean notifyListeners(boolean pressed, ControllerType hand) {
        for (KeyListener listener : this.listeners) {
            if (pressed) {
                if (listener.onPressed(hand)) {
                    return true;
                }
            } else if (listener.onUnpressed(hand)) {
                return true;
            }
        }

        return false;
    }

    public void tick() {
        if (this.isHanded()) {
            for (int c = 0; c < ControllerType.values().length; c++) {
                if (this.unpressInTicks[c] > 0 && --this.unpressInTicks[c] == 0) {
                    this.unpressBindingImmediately(ControllerType.values()[c]);
                }
            }
        } else if (this.unpressInTicks[0] > 0 && --this.unpressInTicks[0] == 0) {
            this.unpressBindingImmediately(null);
        }
    }

    private void pressBinding(ControllerType hand) {
        if (this.isHanded()) {
            if (hand == null || this.pressed[hand.ordinal()]) return;

            this.pressed[hand.ordinal()] = true;

            if (this.notifyListeners(true, hand)) return;

            ((HandedKeyBinding) this.keyBinding).pressKey(hand);
        } else {
            if (this.pressed[0]) return;

            this.pressed[0] = true;

            if (this.notifyListeners(true, null)) return;

            this.pressKey();
        }
    }

    public void pressBinding() {
        this.pressBinding(this.currentHand);
    }

    public void unpressBinding(int unpressInTicks, ControllerType hand) {
        if (this.isHanded()) {
            if (hand == null || !this.pressed[hand.ordinal()]) return;

            this.unpressInTicks[hand.ordinal()] = unpressInTicks;
        } else {
            if (!this.pressed[0]) return;

            this.unpressInTicks[0] = unpressInTicks;
        }
    }

    public void unpressBinding(int unpressInTicks) {
        this.unpressBinding(unpressInTicks, this.currentHand);
    }

    public void unpressBinding() {
        this.unpressBinding(1);
    }

    public void unpressBindingImmediately(ControllerType hand) {
        if (this.isHanded()) {
            if (hand == null || !this.pressed[hand.ordinal()]) return;

            this.pressed[hand.ordinal()] = false;

            if (this.notifyListeners(false, hand)) return;

            ((HandedKeyBinding) this.keyBinding).unpressKey(hand);
        } else {
            if (!this.pressed[0]) return;

            this.pressed[0] = false;

            if (this.notifyListeners(false, null)) return;

            this.unpressKey();
        }
    }

    public static void setKeyBindState(KeyMapping keyMapping, boolean pressed) {
        if (keyMapping != null) {
            keyMapping.setDown(pressed);
            keyMapping.clickCount += 1;
        }
    }

    /**
     * presses the KeyMapping assigned to this InputAction <br>
     * if the KeyMapping has a modifier key also presses that
     */
    private void pressKey() {
        InputConstants.Key key = this.keyBinding.key;

        // need to simulate the modifier or the binding wouldn't be pressed
        if (key.getValue() != -1 && (!VivecraftVRMod.INSTANCE.isSafeBinding(this.keyBinding) || Xplat.hasKeyModifier(this.keyBinding))) {
            if (key.getType() == InputConstants.Type.KEYSYM) {
                if (Xplat.hasKeyModifier(this.keyBinding)) {
                    InputSimulator.pressModifier(Xplat.getKeyModifierKey(this.keyBinding));
                }
                InputSimulator.pressKey(key.getValue(), Xplat.getKeyModifier(this.keyBinding));
                return;
            }

            if (key.getType() == InputConstants.Type.MOUSE) {
                InputSimulator.pressMouse(key.getValue());
                return;
            }
        }

        setKeyBindState(this.keyBinding, true);
    }

    /**
     * unpresses the KeyMapping assigned to this InputAction <br>
     * if the KeyMapping has a modifier key also unpresses that
     */
    public void unpressKey() {
        InputConstants.Key key = this.keyBinding.key;

        if (key.getValue() != -1 && (!VivecraftVRMod.INSTANCE.isSafeBinding(this.keyBinding) || Xplat.hasKeyModifier(this.keyBinding))) {
            if (key.getType() == InputConstants.Type.KEYSYM) {
                InputSimulator.releaseKey(key.getValue());
                if (Xplat.hasKeyModifier(this.keyBinding)) {
                    InputSimulator.releaseModifier(Xplat.getKeyModifierKey(this.keyBinding));
                }
                return;
            }

            if (key.getType() == InputConstants.Type.MOUSE) {
                InputSimulator.releaseMouse(key.getValue());
                return;
            }
        }

        this.keyBinding.release();
    }

    public static class AnalogData {
        public float x;
        public float y;
        public float z;
        public float deltaX;
        public float deltaY;
        public float deltaZ;
        public boolean isChanged;
        public boolean isActive;
        public long activeOrigin;
    }

    public static class DigitalData {
        public boolean state;
        public boolean isChanged;
        public boolean isActive;
        public long activeOrigin;
    }

    public interface KeyListener {
        boolean onPressed(@Nullable ControllerType controllerType);

        boolean onUnpressed(@Nullable ControllerType controllerType);

        int getPriority();
    }
}
