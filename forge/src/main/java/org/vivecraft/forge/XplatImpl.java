package org.vivecraft.forge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.nio.file.Path;

public class XplatImpl {

    public static boolean isModLoaded(String name) {
        return FMLLoader.getLoadingModList().getModFileById(name) != null;
    }

    public static Path getConfigPath(String fileName) {
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }

    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    public static String getModloader() {
        return "forge";
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
}
