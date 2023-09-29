package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.VRData.VRDevicePose;
import org.vivecraft.client_vr.render.RenderPass;

import static org.joml.Math.abs;
import static org.joml.Math.min;
import static org.vivecraft.client_vr.VRState.dh;
import static org.vivecraft.client_vr.VRState.mc;

public class TelescopeTracker extends Tracker {
    //public static final ResourceLocation scopeResource = new ResourceLocation("vivecraft:trashbin");
    public static final ModelResourceLocation scopeModel = new ModelResourceLocation("vivecraft", "spyglass_in_hand", "inventory");
    private static final double lensDistMax = 0.05D;
    private static final double lensDistMin = 0.185D;
    private static final double lensDotMax = 0.9D;
    private static final double lensDotMin = 0.75D;

    public static boolean isTelescope(ItemStack i) {
        return i != null && (i.getItem() == Items.SPYGLASS || isLegacyTelescope(i) || i.is(ItemTags.VIVECRAFT_TELESCOPE));
    }

    // TODO: old eye of the farseer, remove this eventually
    public static boolean isLegacyTelescope(ItemStack i) {
        if (i.isEmpty()) {
            return false;
        } else if (!i.hasCustomHoverName()) {
            return false;
        } else if (i.getItem() != Items.ENDER_EYE) {
            return false;
        } else if (!i.hasTag() || !i.getTag().getBoolean("Unbreakable")) {
            return false;
        } else {
            return i.getHoverName().getContents() instanceof TranslatableContents && "vivecraft.item.telescope".equals(((TranslatableContents) i.getHoverName().getContents()).getKey()) || "Eye of the Farseer".equals(i.getHoverName().getString());
        }
    }

    private static Vec3 getLensOrigin(int controller) {
        VRDevicePose vrdata$vrdevicepose = dh.vrPlayer.vrdata_room_pre.getController(controller);
        return vrdata$vrdevicepose.getPosition().add(getViewVector(controller).scale(-0.2D).add(vrdata$vrdevicepose.getDirection().scale((double) 0.05F)));
    }

    private static Vec3 getViewVector(int controller) {
        return dh.vrPlayer.vrdata_room_pre.getController(controller).getCustomVector(new Vec3(0.0D, -1.0D, 0.0D));
    }

    public static boolean isViewing(int controller) {
        return viewPercent(controller) > 0.0F;
    }

    public static float viewPercent(int controller) {
        if (mc.player != null && dh.vrSettings.seated) {
            if (isTelescope(mc.player.getUseItem())) {
                return 1;
            } else {
                return 0;
            }
        }

        float out = 0.0F;

        for (int e = 0; e < 2; ++e) {
            float tmp = viewPercent(controller, e);

            if (tmp > out) {
                out = tmp;
            }
        }

        return out;
    }

    private static float viewPercent(int controller, int e) {
        if (e == -1) {
            return 0.0F;
        } else {
            VRDevicePose eye = dh.vrPlayer.vrdata_room_pre.getEye(RenderPass.values()[e]);
            double dist = eye.getPosition().subtract(getLensOrigin(controller)).length();
            Vec3 look = eye.getDirection();
            double dot = abs(look.dot(getViewVector(controller)));

            double dfact = 0.0D;
            double distfact = 0.0D;

            if (dot > lensDotMin) {
                if (dot > lensDotMax) {
                    dfact = 1.0D;
                } else {
                    dfact = (dot - lensDotMin) / (lensDotMax - lensDotMin);
                }
            }

            if (dist < lensDistMin) {
                if (dist < lensDistMax) {
                    distfact = 1.0D;
                } else {
                    distfact = 1.0D - (dist - lensDistMax) / (lensDistMin - lensDistMax);
                }
            }

            return (float) min(dfact, distfact);
        }
    }
}
