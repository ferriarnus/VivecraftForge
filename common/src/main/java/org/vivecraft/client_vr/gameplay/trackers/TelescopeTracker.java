package org.vivecraft.client_vr.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.ItemTags;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.render.RenderPass;

public class TelescopeTracker extends Tracker {
    public static final ModelResourceLocation SCOPE_MODEL = new ModelResourceLocation("vivecraft", "spyglass_in_hand", "inventory");
    private static final double LENS_DIST_MAX = 0.05D;
    private static final double LENS_DIST_MIN = 0.185D;
    private static final double LENS_DOT_MAX = 0.9D;
    private static final double LENS_DOT_MIN = 0.75D;

    public TelescopeTracker(Minecraft mc, ClientDataHolderVR dh) {
        super(mc, dh);
    }

    @Override
    public boolean isActive(LocalPlayer player) {
        return false;
    }

    @Override
    public void doProcess(LocalPlayer player) {
    }

    /**
     * @param itemStack ItemStack to check
     * @return if the given {@code itemStack} is a telescope
     */
    public static boolean isTelescope(ItemStack itemStack) {
        return itemStack != null && (itemStack.getItem() == Items.SPYGLASS || isLegacyTelescope(itemStack) ||
            itemStack.is(ItemTags.VIVECRAFT_TELESCOPE)
        );
    }

    // TODO: old eye of the farseer, remove this eventually
    public static boolean isLegacyTelescope(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        } else if (!itemStack.hasCustomHoverName()) {
            return false;
        } else if (itemStack.getItem() != Items.ENDER_EYE) {
            return false;
        } else if (!itemStack.hasTag() || !itemStack.getTag().getBoolean("Unbreakable")) {
            return false;
        } else {
            return itemStack.getHoverName().getString().equals("Eye of the Farseer") ||
                (itemStack.getHoverName().getContents() instanceof TranslatableContents translatableContents &&
                    translatableContents.getKey().equals("vivecraft.item.telescope")
                );
        }
    }

    private static Vec3 getLensOrigin(int controller) {
        VRData.VRDevicePose con = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getController(controller);
        return con.getPosition().add(getViewVector(controller).scale(-0.2D).add(con.getDirection().scale(0.05F)));
    }

    private static Vec3 getViewVector(int controller) {
        return ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getController(controller).getCustomVector(new Vec3(0.0D, -1.0D, 0.0D));
    }

    public static boolean isViewing(int controller) {
        return viewPercent(controller) > 0.0F;
    }

    public static float viewPercent(int controller) {
        // seated doesn't have a fadeout
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ClientDataHolderVR.getInstance().vrSettings.seated) {
            if (isTelescope(player.getUseItem())) {
                return 1;
            } else {
                return 0;
            }
        }

        float out = 0.0F;

        for (int e = 0; e < 2; e++) {
            float tmp = viewPercent(controller, e);

            if (tmp > out) {
                out = tmp;
            }
        }

        return out;
    }

    private static float viewPercent(int controller, int e) {
        if (e == -1 || ClientDataHolderVR.getInstance().vrPlayer == null) {
            return 0.0F;
        } else {
            VRData.VRDevicePose eye = ClientDataHolderVR.getInstance().vrPlayer.vrdata_room_pre.getEye(RenderPass.values()[e]);
            double dist = eye.getPosition().subtract(getLensOrigin(controller)).length();
            Vec3 look = eye.getDirection();
            double dot = Math.abs(look.dot(getViewVector(controller)));

            double dfact = 0.0D;
            double distfact = 0.0D;

            if (dot > LENS_DOT_MIN) {
                if (dot > LENS_DOT_MAX) {
                    dfact = 1.0D;
                } else {
                    dfact = (dot - LENS_DOT_MIN) / (LENS_DOT_MAX - LENS_DOT_MIN);
                }
            }

            if (dist < LENS_DIST_MIN) {
                if (dist < LENS_DIST_MAX) {
                    distfact = 1.0D;
                } else {
                    distfact = 1.0D - (dist - LENS_DIST_MAX) / (LENS_DIST_MIN - LENS_DIST_MAX);
                }
            }

            return (float) Math.min(dfact, distfact);
        }
    }
}
