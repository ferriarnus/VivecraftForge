package org.vivecraft.client.forge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import io.netty.buffer.Unpooled;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.NetworkDirection;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client.Xplat;
import org.vivecraft.common.network.packet.c2s.VivecraftPayloadC2S;
import org.vivecraft.common.network.packet.s2c.VivecraftPayloadS2C;

import java.nio.file.Path;

public class XplatImpl implements Xplat {

    public static void init() {}

    public static boolean isModLoaded(String name) {
        return FMLLoader.getLoadingModList().getModFileById(name) != null;
    }

    public static Path getConfigPath(String fileName) {
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }

    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    public static Xplat.ModLoader getModloader() {
        return Xplat.ModLoader.FORGE;
    }

    public static String getModVersion() {
        if (isModLoadedSuccess()) {
            return FMLLoader.getLoadingModList().getModFileById("vivecraft").versionString();
        }
        return "no version";
    }

    public static boolean isModLoadedSuccess() {
        return FMLLoader.getLoadingModList().getModFileById("vivecraft") != null;
    }

    public static boolean enableRenderTargetStencil(RenderTarget renderTarget) {
        renderTarget.enableStencil();
        return true;
    }

    public static Path getJarPath() {
        return FMLLoader.getLoadingModList().getModFileById("vivecraft").getFile().getSecureJar().getPath("/");
    }

    public static String getUseMethodName() {
        return ObfuscationReflectionHelper.findMethod(
            net.minecraft.world.level.block.state.BlockBehaviour.class,
            "m_6227_",
            net.minecraft.world.level.block.state.BlockState.class,
            net.minecraft.world.level.Level.class,
            net.minecraft.core.BlockPos.class,
            net.minecraft.world.entity.player.Player.class,
            net.minecraft.world.InteractionHand.class,
            net.minecraft.world.phys.BlockHitResult.class).getName();
    }

    public static TextureAtlasSprite[] getFluidTextures(
        BlockAndTintGetter level, BlockPos pos, FluidState fluidStateIn)
    {
        return ForgeHooksClient.getFluidSprites(level, pos, fluidStateIn);
    }

    public static Biome.ClimateSettings getBiomeClimateSettings(Biome biome) {
        return biome.getModifiedClimateSettings();
    }

    public static BiomeSpecialEffects getBiomeEffects(Biome biome) {
        return biome.getModifiedSpecialEffects();
    }

    public static double getItemEntityReach(double baseRange, ItemStack itemStack, EquipmentSlot slot) {
        var attributes = itemStack.getAttributeModifiers(slot).get(ForgeMod.ENTITY_REACH.get());
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
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buffer);
        return NetworkDirection.PLAY_TO_SERVER.buildPacket(buffer, payload.id()).getThis();
    }

    public static Packet<?> getS2CPacket(VivecraftPayloadS2C payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buffer);
        return NetworkDirection.PLAY_TO_CLIENT.buildPacket(buffer, payload.id()).getThis();
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
