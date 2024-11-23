package org.vivecraft.mod_compat_vr.sodium;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Vector2f;
import org.vivecraft.client.Xplat;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.mod_compat_vr.sodium.extensions.ModelCuboidExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SodiumHelper {

    // use reflection, because sodium changed package in 0.6
    private static Method SpriteUtil_MarkSpriteActive;

    private static boolean hasModelCuboidQuads;
    private static boolean hasModelCuboidFloats;
    private static boolean hasCubeModelCuboid;
    private static Field ModelCuboid_Sodium$cuboids;
    private static Field ModelCuboid_Quads;

    private static Field Cube_Sodium$cuboid;

    // quad uvs
    private static Field ModelCuboid_u0;
    private static Field ModelCuboid_u1;
    private static Field ModelCuboid_u2;
    private static Field ModelCuboid_u3;
    private static Field ModelCuboid_u4;
    private static Field ModelCuboid_u5;
    private static Field ModelCuboid_v0;
    private static Field ModelCuboid_v1;
    private static Field ModelCuboid_v2;

    private static Field ModelCuboid$Quad_Textures;

    private static boolean initialized = false;
    private static boolean initFailed = false;

    public static boolean isLoaded() {
        return Xplat.isModLoaded("sodium") || Xplat.isModLoaded("rubidium") || Xplat.isModLoaded("embeddium");
    }

    public static boolean hasIssuesWithParallelBlockBuilding() {
        try {
            Class.forName("me.jellysquid.mods.sodium.client.render.immediate.model.BakedModelEncoder");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static void markTextureAsActive(TextureAtlasSprite sprite) {
        if (init()) {
            try {
                // SpriteUtil.markSpriteActive(sprite);
                SpriteUtil_MarkSpriteActive.invoke(null, sprite);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

// NotFixed
//    public static void vignette(boolean b) {
//        SodiumClientMod.options().quality.enableVignette = b;
//    }

    public static void copyModelCuboidUV(ModelPart source, ModelPart dest, int sourcePoly, int destPoly) {
        if (init()) {
            if (hasModelCuboidQuads) {
                try {
                    Object sourceQuad = ((Object[]) ModelCuboid_Quads.get(((Object[]) ModelCuboid_Sodium$cuboids.get(source))[0]))[sourcePoly];
                    Object destQuad = ((Object[]) ModelCuboid_Quads.get(((Object[]) ModelCuboid_Sodium$cuboids.get(dest))[0]))[destPoly];

                    Vector2f[] sourceTextures = (Vector2f[]) ModelCuboid$Quad_Textures.get(sourceQuad);
                    Vector2f[] destTextures = (Vector2f[]) ModelCuboid$Quad_Textures.get(destQuad);

                    for (int i = 0; i < sourceTextures.length; i++) {
                        destTextures[i].x = sourceTextures[i].x;
                        destTextures[i].y = sourceTextures[i].y;
                    }
                } catch (IllegalAccessException | ClassCastException ignored) {
                    VRSettings.logger.error("Vivecraft: sodium version has ModelCuboids, but field has wrong type. VR hands will probably look wrong");
                    hasModelCuboidQuads = false;
                }
            } else if (hasModelCuboidFloats) {
                try {
                    Object sourceCuboid = hasCubeModelCuboid ? Cube_Sodium$cuboid.get(source.cubes.get(0)) : ((Object[]) ModelCuboid_Sodium$cuboids.get(source))[0];
                    float[][] UVs = new float[][]{{
                        (float) ModelCuboid_u0.get(sourceCuboid),
                        (float) ModelCuboid_u1.get(sourceCuboid),
                        (float) ModelCuboid_u2.get(sourceCuboid),
                        (float) ModelCuboid_u3.get(sourceCuboid),
                        (float) ModelCuboid_u4.get(sourceCuboid),
                        (float) ModelCuboid_u5.get(sourceCuboid)
                    }, {
                        (float) ModelCuboid_v0.get(sourceCuboid),
                        (float) ModelCuboid_v1.get(sourceCuboid),
                        (float) ModelCuboid_v2.get(sourceCuboid)
                    }};

                    Object destCuboid = hasCubeModelCuboid ? Cube_Sodium$cuboid.get(dest.cubes.get(0)) : ((Object[]) ModelCuboid_Sodium$cuboids.get(dest))[0];
                    ((ModelCuboidExtension) destCuboid).vivecraft$addOverrides(
                        mapDirection(destPoly),
                        mapDirection(sourcePoly),
                        UVs
                    );
                } catch (IllegalAccessException | ClassCastException ignored) {
                    VRSettings.logger.error("Vivecraft: sodium version has ModelCuboids, but field has wrong type. VR hands will probably look wrong");
                    hasModelCuboidFloats = false;
                }
            }
        }
    }

    private static int mapDirection(int old) {
        return switch (old) {
            default -> 4;
            case 1 -> 2;
            case 2 -> 0;
            case 3 -> 1;
            case 4 -> 3;
            case 5 -> 5;
        };
    }

    private static boolean init() {
        if (initialized) {
            // try to softly fail when something went wrong
            return !initFailed;
        }
        try {
            Class<?> spriteUtil = getClassWithAlternative(
                "me.jellysquid.mods.sodium.client.render.texture.SpriteUtil",
                "net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil"
            );

            SpriteUtil_MarkSpriteActive = spriteUtil.getMethod("markSpriteActive", TextureAtlasSprite.class);

            try {
                // model
                Class<?> ModelCuboid = getClassWithAlternative(
                    "me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid",
                    "net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid"
                );

                try {
                    ModelCuboid_Sodium$cuboids = ModelPart.class.getDeclaredField("sodium$cuboids");
                    ModelCuboid_Sodium$cuboids.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                    Cube_Sodium$cuboid = ModelPart.Cube.class.getDeclaredField("sodium$cuboid");
                    Cube_Sodium$cuboid.setAccessible(true);
                    hasCubeModelCuboid = true;

                }
                try {
                    Class<?> cuboidQuad = getClassWithAlternative(
                        "me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid$Quad",
                        "net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid$Quad"
                    );
                    ModelCuboid_Quads = ModelCuboid.getDeclaredField("quads");
                    ModelCuboid$Quad_Textures = cuboidQuad.getDeclaredField("textures");
                    hasModelCuboidQuads = true;
                } catch (ClassNotFoundException noQuads) {
                    ModelCuboid_u0 = ModelCuboid.getDeclaredField("u0");
                    ModelCuboid_u1 = ModelCuboid.getDeclaredField("u1");
                    ModelCuboid_u2 = ModelCuboid.getDeclaredField("u2");
                    ModelCuboid_u3 = ModelCuboid.getDeclaredField("u3");
                    ModelCuboid_u4 = ModelCuboid.getDeclaredField("u4");
                    ModelCuboid_u5 = ModelCuboid.getDeclaredField("u5");
                    ModelCuboid_v0 = ModelCuboid.getDeclaredField("v0");
                    ModelCuboid_v1 = ModelCuboid.getDeclaredField("v1");
                    ModelCuboid_v2 = ModelCuboid.getDeclaredField("v2");
                    hasModelCuboidFloats = true;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (NoSuchFieldException e) {
                VRSettings.logger.error("Vivecraft: sodium version has ModelCuboids, but field was not found. VR hands will probably look wrong");
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            initFailed = true;
            VRSettings.logger.error("Vivecraft: Failed to initialize Sodium compat: {}", e.getMessage());
        }
        initialized = true;
        return !initFailed;
    }

    private static Class<?> getClassWithAlternative(String class1, String class2) throws ClassNotFoundException {
        try {
            return Class.forName(class1);
        } catch (ClassNotFoundException e) {
            return Class.forName(class2);
        }
    }
}
