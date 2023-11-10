package org.vivecraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FluidState;
import org.vivecraft.common.network.packet.c2s.VivecraftPayloadC2S;
import org.vivecraft.common.network.packet.s2c.VivecraftPayloadS2C;

import java.nio.file.Path;

public interface Xplat {
    /**
     * This must be a <b>public static</b> method. The platform-implemented solution must be placed under a
     * platform sub-package, with its class suffixed with {@code Impl}.
     * <p>
     * Example:<br>
     * Expect: net.examplemod.ExampleExpectPlatform#getConfigDirectory()<br>
     * Actual Fabric: net.examplemod.fabric.ExampleExpectPlatformImpl#getConfigDirectory()<br>
     * Actual Forge: net.examplemod.forge.ExampleExpectPlatformImpl#getConfigDirectory()<br>
     * <p>
     * <a href="https://plugins.jetbrains.com/plugin/16210-architectury">You should also get the IntelliJ plugin to help with @ExpectPlatform.</a>
     */

    enum ModLoader {
        FABRIC("fabric"),
        FORGE("forge"),
        NEOFORGE("neoforge"),
        QUILT("quilt");

        public final String name;

        ModLoader(String name) {
            this.name = name;
        }

        public boolean isFabric() {
            return this == FABRIC || this == QUILT;
        }
    }

    /**
     * initializes stuff needed for the Xplat to work correctly
     */
    @ExpectPlatform
    static void init() {}

    /**
     * @param name modId to check
     * @return if the mod {@code name} is loaded
     */
    @ExpectPlatform
    static boolean isModLoaded(String name) {
        return false;
    }

    /**
     * asks the mod loader for the config folder, and resolves the given file there
     *
     * @param file file to get the path for
     * @return Path of {@code file} in the config folder
     */
    @ExpectPlatform
    static Path getConfigPath(String file) {
        throw new AssertionError();
    }

    /**
     * @return true if this is a dedicated server
     */
    @ExpectPlatform
    static boolean isDedicatedServer() {
        return false;
    }

    /**
     * @return mod loader enum that the game is running on
     */
    @ExpectPlatform
    static ModLoader getModloader() {
        throw new AssertionError();
    }

    /**
     * @return version number of the vivecraft mod
     */
    @ExpectPlatform
    static String getModVersion() {
        return "";
    }

    /**
     * @return returns true, if the mod loader loaded everything without errors
     */
    @ExpectPlatform
    static boolean isModLoadedSuccess() {
        return false;
    }

    /**
     * asks the mod loader to enable the stencil for the given RenderTarget
     *
     * @param renderTarget RenderTarget to enable the Stencil on
     * @return true if the mod loader enabled the stencil
     */
    @ExpectPlatform
    static boolean enableRenderTargetStencil(RenderTarget renderTarget) {
        return false;
    }

    /**
     * @return path to access files inside the mod jar
     */
    @ExpectPlatform
    static Path getJarPath() {
        throw new AssertionError();
    }

    /**
     * @return runtime name of the {@link BlockBehaviour#use} method
     */
    @ExpectPlatform
    static String getUseMethodName() {
        return "";
    }

    /**
     * gets the TextureAtlasSprites for the given FluidState
     *
     * @param level      level the fluid is in
     * @param pos        BlockPos of the fluid
     * @param fluidState State of the fluid
     * @return array of the textures of a fluid block
     */
    @ExpectPlatform
    static TextureAtlasSprite[] getFluidTextures(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        return new TextureAtlasSprite[]{};
    }

    /**
     * @param biome Biome to get the ClimateSettings from
     * @return ClimateSettings of the given Biome
     */
    @ExpectPlatform
    static Biome.ClimateSettings getBiomeClimateSettings(Biome biome) {
        throw new AssertionError();
    }

    /**
     * @param biome Biome to get the BiomeSpecialEffects from
     * @return BiomeSpecialEffects of the given Biome
     */
    @ExpectPlatform
    static BiomeSpecialEffects getBiomeEffects(Biome biome) {
        throw new AssertionError();
    }

    /**
     * modifies {@code baseRange} with any modifiers for the entity reach of the given ItemStack
     *
     * @param baseRange base item reach to start with
     * @param itemStack ItemStack to use the modifiers from
     * @param slot      slot where the item is in
     * @return modified range, if there are no changes then {@code baseRange} is returned
     */
    @ExpectPlatform
    static double getItemEntityReach(double baseRange, ItemStack itemStack, EquipmentSlot slot) {
        return 0;
    }

    /**
     * wraps the given payload into the mod loader specific packet
     *
     * @param payload payload to wrap
     * @return ServerboundCustomPayloadPacket
     */
    @ExpectPlatform
    static Packet<?> getC2SPacket(VivecraftPayloadC2S payload) {
        throw new AssertionError();
    }

    /**
     * wraps the given payload into the mod loader specific packet
     *
     * @param payload payload to wrap
     * @return ClientboundCustomPayloadPacket
     */
    @ExpectPlatform
    static Packet<?> getS2CPacket(VivecraftPayloadS2C payload) {
        throw new AssertionError();
    }

    /**
     * checks if the given KeyMapping uses a key modifier to trigger
     *
     * @param keyMapping KeyMapping to check
     * @return true if a key modifier is used
     */
    @ExpectPlatform
    static boolean hasKeyModifier(KeyMapping keyMapping) {
        return false;
    }

    /**
     * gets the key modifier for the given KeyMapping
     *
     * @param keyMapping KeyMapping to check
     * @return one of the GLFW_MOD_X modifiers, or 0 if there is none
     */
    @ExpectPlatform
    static int getKeyModifier(KeyMapping keyMapping) {
        return 0;
    }

    /**
     * gets the key that corresponds to the key modifier for the given KeyMapping
     *
     * @param keyMapping KeyMapping to check
     * @return one of the GLFW_KEY_X keys, or -1 if there is none
     */
    @ExpectPlatform
    static int getKeyModifierKey(KeyMapping keyMapping) {
        return -1;
    }
}
