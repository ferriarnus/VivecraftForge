package org.vivecraft.client.neoforge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.Xplat;
import org.vivecraft.common.network.packet.c2s.VivecraftPayloadC2S;
import org.vivecraft.common.network.packet.s2c.VivecraftPayloadS2C;
import org.vivecraft.neoforge.packet.VivecraftPayloadBiDir;

import java.nio.file.Path;

public class XplatImpl implements Xplat {

    public static void init() {}

    public static boolean isModLoaded(String name) {
        return LoadingModList.get().getModFileById(name) != null;
    }

    public static Path getConfigPath(String fileName) {
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }

    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    public static ModLoader getModloader() {
        return ModLoader.NEOFORGE;
    }

    public static String getModVersion() {
        if (isModLoadedSuccess()) {
            return LoadingModList.get().getModFileById("vivecraft").versionString();
        }
        return "no version";
    }

    public static boolean isModLoadedSuccess() {
        return LoadingModList.get().getModFileById("vivecraft") != null;
    }

    public static boolean enableRenderTargetStencil(RenderTarget renderTarget) {
        renderTarget.enableStencil();
        return true;
    }

    public static Path getJarPath() {
        return LoadingModList.get().getModFileById("vivecraft").getFile().getSecureJar().getPath("/");
    }

    public static String getUseMethodName() {
        return "use";
    }

    public static TextureAtlasSprite[] getFluidTextures(
        BlockAndTintGetter level, BlockPos pos, FluidState fluidStateIn)
    {
        return FluidSpriteCache.getFluidSprites(level, pos, fluidStateIn);
    }

    public static Biome.ClimateSettings getBiomeClimateSettings(Biome biome) {
        return biome.getModifiedClimateSettings();
    }

    public static BiomeSpecialEffects getBiomeEffects(Biome biome) {
        return biome.getModifiedSpecialEffects();
    }

    public static double getItemEntityReach(double baseRange, ItemStack itemStack, EquipmentSlot slot) {
        var attributes = itemStack.getAttributeModifiers(slot).get(NeoForgeMod.ENTITY_REACH.value());
        for (var a : attributes) {
            if (a.getOperation() == AttributeModifier.Operation.ADDITION) {
                baseRange += a.getAmount();
            }
        }
        double totalRange = baseRange;
        for (var a : attributes) {
            if (a.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) {
                totalRange += baseRange * a.getAmount();
            }
        }
        for (var a : attributes) {
            if (a.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                totalRange *= 1.0 + a.getAmount();
            }
        }
        return totalRange;
    }

    public static Packet<?> getC2SPacket(VivecraftPayloadC2S payload) {
        return new ServerboundCustomPayloadPacket(new VivecraftPayloadBiDir(payload));
    }

    public static Packet<?> getS2CPacket(VivecraftPayloadS2C payload) {
        return new ClientboundCustomPayloadPacket(new VivecraftPayloadBiDir(payload));
    }

    public static boolean hasKeyModifier(KeyMapping keyMapping) {
        return keyMapping.getKeyModifier() != KeyModifier.NONE;
    }

    public static int getKeyModifier(KeyMapping keyMapping) {
        return switch (keyMapping.getKeyModifier()) {
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case ALT -> GLFW.GLFW_MOD_ALT;
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            default -> 0;
        };
    }

    public static int getKeyModifierKey(KeyMapping keyMapping) {
        return switch (keyMapping.getKeyModifier()) {
            case SHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case ALT -> GLFW.GLFW_KEY_RIGHT_ALT;
            case CONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            default -> -1;
        };
    }
}
