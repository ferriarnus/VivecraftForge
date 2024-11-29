package org.vivecraft.client.render.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

public class VRArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> extends HumanoidArmorLayer<T, M, A> {

    // split arms model
    public static LayerDefinition VR_ARMOR_DEF_ARMS_INNER;
    public static LayerDefinition VR_ARMOR_DEF_ARMS_OUTER;

    // split arms/legs model
    public static LayerDefinition VR_ARMOR_DEF_ARMS_LEGS_INNER;
    public static LayerDefinition VR_ARMOR_DEF_ARMS_LEGS_OUTER;

    static {
        // need to make these not final, because they change depending on settings
        createLayers();
    }

    public static void createLayers() {
        // split arms model
        VR_ARMOR_DEF_ARMS_INNER = LayerDefinition.create(
            VRArmorModel_WithArms.createBodyLayer(new CubeDeformation(0.5F)), 64, 32);
        VR_ARMOR_DEF_ARMS_OUTER = LayerDefinition.create(
            VRArmorModel_WithArms.createBodyLayer(new CubeDeformation(1.0F)), 64, 32);

        // split arms/legs model
        VR_ARMOR_DEF_ARMS_LEGS_INNER = LayerDefinition.create(
            VRArmorModel_WithArmsLegs.createBodyLayer(new CubeDeformation(0.5F)), 64, 32);
        VR_ARMOR_DEF_ARMS_LEGS_OUTER = LayerDefinition.create(
            VRArmorModel_WithArmsLegs.createBodyLayer(new CubeDeformation(1.0F)), 64, 32);
    }

    public VRArmorLayer(RenderLayerParent<T, M> renderer, A innerModel, A outerModel, ModelManager modelManager) {
        super(renderer, innerModel, outerModel, modelManager);
    }

    @Override
    protected void setPartVisibility(A model, EquipmentSlot slot) {
        super.setPartVisibility(model, slot);
        switch (slot) {
            case CHEST -> {
                if (model instanceof VRArmorModel_WithArms<?> armsModel) {
                    armsModel.leftHand.visible = true;
                    armsModel.rightHand.visible = true;
                }
            }
            case LEGS -> {
                if (model instanceof VRArmorModel_WithArmsLegs<?> legsModel) {
                    legsModel.leftFoot.visible = true;
                    legsModel.rightFoot.visible = true;
                }
            }
            case FEET -> {
                if (model instanceof VRArmorModel_WithArmsLegs<?> legsModel) {
                    // don't show the upper half for the feet
                    legsModel.leftLeg.visible = false;
                    legsModel.rightLeg.visible = false;

                    legsModel.leftFoot.visible = true;
                    legsModel.rightFoot.visible = true;
                }
            }
        }
    }
}
