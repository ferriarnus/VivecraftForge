package org.vivecraft.mixin.client.renderer.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client.extensions.EntityRenderDispatcherExtension;
import org.vivecraft.client.extensions.RenderLayerExtension;
import org.vivecraft.client.utils.RenderLayerType;
import org.vivecraft.client_vr.settings.VRSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * A hacky way of copying regular PlayerRenderer layers to the VRPlayerRenderers
 * an alternative would be to add the VRPlayerRenderers to the skin model list,
 * so mods could add it manually, but some mods hardcode only the slim/default model,
 * and that would mean the VRPlayerRenderers would be missing those layers completely
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {

    @Shadow
    protected M model;

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "addLayer", at = @At("HEAD"))
    private void vivecraft$copyLayer(RenderLayer<T, M> renderLayer, CallbackInfoReturnable<Boolean> cir) {
        // check if the layer gets added from the PlayerRenderer, we don't want to copy, if we add it to the VRPlayerRenderer
        // also check that the VRPlayerRenderers were created, this method also gets called in the constructor,
        // those default Layers already are added to the VRPlayerRenderer there
        EntityRenderDispatcherExtension renderExtension = (EntityRenderDispatcherExtension) this.entityRenderDispatcher;
        if ((Object) this.getClass() == PlayerRenderer.class && !renderExtension.vivecraft$getSkinMapVRSeated().isEmpty()) {

            // try to find a suitable constructor, so we can create a new Object without issues
            Constructor<?> constructor = null;
            RenderLayerType type = RenderLayerType.OTHER;
            for (Constructor<?> c : renderLayer.getClass().getConstructors()) {
                if (c.getParameterCount() == 1 &&
                    RenderLayerParent.class.isAssignableFrom(c.getParameterTypes()[0]))
                {
                    constructor = c;
                    type = RenderLayerType.PARENT_ONLY;
                    break;
                } else if (c.getParameterCount() == 2 &&
                    RenderLayerParent.class.isAssignableFrom(c.getParameterTypes()[0]) &&
                    EntityModelSet.class.isAssignableFrom(c.getParameterTypes()[1]))
                {
                    constructor = c;
                    type = RenderLayerType.PARENT_MODELSET;
                } else if (c.getParameterCount() == 3 &&
                    RenderLayerParent.class.isAssignableFrom(c.getParameterTypes()[0]) &&
                    HumanoidModel.class.isAssignableFrom(c.getParameterTypes()[1]) &&
                    HumanoidModel.class.isAssignableFrom(c.getParameterTypes()[2]) &&
                    renderLayer instanceof HumanoidArmorLayer)
                {
                    constructor = c;
                    type = RenderLayerType.PARENT_MODEL_MODEL;
                }
            }

            // if no suitable constructor was found, use do a basic Object.clone call, and replace the parent of the copy
            if (constructor == null) {
                // do a hacky clone, and replace parent
                if (((PlayerModel<?>) this.model).slim &&
                    !renderExtension.vivecraft$getSkinMapVRSeated().get("slim").hasLayerType(renderLayer)) {
                    vivecraft$addLayerClone(renderLayer, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVRSeated().get("slim"));
                    vivecraft$addLayerClone(renderLayer, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVR().get("slim"));
                } else if (!renderExtension.vivecraft$getSkinMapVRSeated().get("default").hasLayerType(renderLayer)) {
                    vivecraft$addLayerClone(renderLayer, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVRSeated().get("default"));
                    vivecraft$addLayerClone(renderLayer, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVR().get("default"));
                }
            } else {
                // make a new instance with the vr model as parent
                if (((PlayerModel<?>) this.model).slim &&
                    !renderExtension.vivecraft$getSkinMapVRSeated().get("slim").hasLayerType(renderLayer)) {
                    vivecraft$addLayerConstructor(constructor, type, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVRSeated().get("slim"));
                    vivecraft$addLayerConstructor(constructor, type, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVR().get("slim"));
                } else if (!renderExtension.vivecraft$getSkinMapVRSeated().get("default").hasLayerType(renderLayer)) {
                    vivecraft$addLayerConstructor(constructor, type, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVRSeated().get("default"));
                    vivecraft$addLayerConstructor(constructor, type, (LivingEntityRenderer<T, M>) renderExtension.vivecraft$getSkinMapVR().get("default"));
                }
            }
        }
    }

    /**
     * does a basic Object.clone() copy
     */
    @SuppressWarnings("unchecked")
    @Unique
    private void vivecraft$addLayerClone(RenderLayer<T, M> renderLayer, LivingEntityRenderer<T, M> target) {
        try {
            VRSettings.LOGGER.warn("Vivecraft: Copying layer: {} with Object.copy, this could cause issues", renderLayer.getClass());
            RenderLayer<T, M> newLayer = (RenderLayer<T, M>) ((RenderLayerExtension) renderLayer).clone();
            newLayer.renderer = target;
            target.addLayer(newLayer);
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * uses the provided constructor, to create a new RenderLayer Instance
     */
    @SuppressWarnings("unchecked")
    @Unique
    private void vivecraft$addLayerConstructor(Constructor<?> constructor, RenderLayerType type, LivingEntityRenderer<T, M> target) {
        try {
            switch (type) {
                case PARENT_ONLY -> target.addLayer((RenderLayer<T, M>) constructor.newInstance(target));
                case PARENT_MODELSET ->
                    target.addLayer((RenderLayer<T, M>) constructor.newInstance(target, Minecraft.getInstance().getEntityModels()));
                case PARENT_MODEL_MODEL -> {
                    if (((PlayerModel<?>) this.model).slim) {
                        target.addLayer((RenderLayer<T, M>) constructor.newInstance(target,
                            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM_INNER_ARMOR)),
                            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM_OUTER_ARMOR))));
                    } else {
                        target.addLayer((RenderLayer<T, M>) constructor.newInstance(target,
                            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                            new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR))));
                    }
                }
            }
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
