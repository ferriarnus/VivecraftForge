package org.vivecraft.client.fabric;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import org.vivecraft.client.Xplat;
import org.vivecraft.common.network.packet.c2s.VivecraftPayloadC2S;
import org.vivecraft.common.network.packet.s2c.VivecraftPayloadS2C;
import org.vivecraft.fabric.mixin.world.level.biome.BiomeAccessor;
import org.vivecraft.fabric.packet.VivecraftFabricPacketC2S;
import org.vivecraft.fabric.packet.VivecraftFabricPacketS2C;

import java.nio.file.Path;

public class XplatImpl implements Xplat {

    private static ModLoader currentModloader = ModLoader.FABRIC;

    public static void init() {
        // check client brand, if we are running on quilt
        if (ClientBrandRetriever.getClientModName().contains("quilt")) {
            currentModloader = ModLoader.QUILT;
        }
    }

    public static boolean isModLoaded(String name) {
        return FabricLoader.getInstance().isModLoaded(name);
    }

    public static Path getConfigPath(String fileName) {
        return FabricLoader.getInstance().getConfigDir().resolve(fileName);
    }

    public static boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType().equals(EnvType.SERVER);
    }

    public static Xplat.ModLoader getModloader() {
        return currentModloader;
    }

    public static String getModVersion() {
        if (isModLoadedSuccess()) {
            return FabricLoader.getInstance().getModContainer("vivecraft").get().getMetadata().getVersion()
                .getFriendlyString();
        }
        return "no version";
    }

    public static boolean isModLoadedSuccess() {
        return FabricLoader.getInstance().isModLoaded("vivecraft");
    }

    public static boolean enableRenderTargetStencil(RenderTarget renderTarget) {
        return false;
    }

    public static Path getJarPath() {
        return FabricLoader.getInstance().getModContainer("vivecraft").get().getRootPaths().get(0);
    }

    public static String getUseMethodName() {
        return FabricLoader.getInstance().getMappingResolver().mapMethodName(
            "intermediary",
            "net.minecraft.class_4970", "method_9534",
            "(Lnet/minecraft/class_2680;" +
                "Lnet/minecraft/class_1937;" +
                "Lnet/minecraft/class_2338;" +
                "Lnet/minecraft/class_1657;" +
                "Lnet/minecraft/class_1268;" +
                "Lnet/minecraft/class_3965;)" +
                "Lnet/minecraft/class_1269;");
    }

    public static TextureAtlasSprite[] getFluidTextures(
        BlockAndTintGetter level, BlockPos pos, FluidState fluidStateIn)
    {
        if (isModLoaded("fabric-rendering-fluids-v1")) {
            return FluidRenderHandlerRegistry.INSTANCE.get(fluidStateIn.getType())
                .getFluidSprites(level, pos, fluidStateIn);
        } else {
            // return vanilla textures
            if (fluidStateIn.is(FluidTags.LAVA)) {
                return new TextureAtlasSprite[]{
                    Minecraft.getInstance().getModelManager().getBlockModelShaper()
                        .getBlockModel(Blocks.LAVA.defaultBlockState()).getParticleIcon(),
                    ModelBakery.LAVA_FLOW.sprite()
                };
            } else {
                return new TextureAtlasSprite[]{
                    Minecraft.getInstance().getModelManager().getBlockModelShaper()
                        .getBlockModel(Blocks.WATER.defaultBlockState()).getParticleIcon(),
                    ModelBakery.WATER_FLOW.sprite()
                };
            }
        }
    }

    public static Biome.ClimateSettings getBiomeClimateSettings(Biome biome) {
        return ((BiomeAccessor) (Object) biome).getClimateSettings();
    }

    public static BiomeSpecialEffects getBiomeEffects(Biome biome) {
        return biome.getSpecialEffects();
    }

    public static double getItemEntityReach(double baseRange, ItemStack itemStack, EquipmentSlot slot) {
        return baseRange;
    }

    public static Packet<?> getC2SPacket(VivecraftPayloadC2S payload) {
        return ClientPlayNetworking.createC2SPacket(new VivecraftFabricPacketC2S(payload));
    }

    public static Packet<?> getS2CPacket(VivecraftPayloadS2C payload) {
        return ServerPlayNetworking.createS2CPacket(new VivecraftFabricPacketS2C(payload));
    }

    public static boolean hasKeyModifier(KeyMapping keyMapping) {
        return false;
    }

    public static int getKeyModifier(KeyMapping keyMapping) {
        return 0;
    }

    public static int getKeyModifierKey(KeyMapping keyMapping) {
        return -1;
    }
}
