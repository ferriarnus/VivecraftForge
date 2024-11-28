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

    private static boolean INITIALIZED = false;
    private static boolean INIT_FAILED = false;

    // use reflection, because sodium changed package in 0.6
    private static Method SpriteUtil_markSpriteActive;

    private static boolean HAS_MODELCUBOID_QUADS;
    private static boolean HAS_MODELCUBOID_FLOATS;
    private static boolean HAS_MODELCUBOID_CUBES;
    private static Field ModelPart_sodium$cuboids;
    private static Field ModelCuboid_quads;

    private static Field Cube_sodium$cuboid;

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

    private static Field ModelCuboid$Quad_textures;

    public static boolean isLoaded() {
        return Xplat.isModLoaded("sodium") || Xplat.isModLoaded("rubidium") || Xplat.isModLoaded("embeddium");
    }

    /**
     * sodium does some mixins into BlockRenderer, calling it multiple times on first load causes issues with mixin applying multiple times
     * @return if blockmodels shouldn't be built in parallel immediately
     */
    public static boolean hasIssuesWithParallelBlockBuilding() {
        try {
            Class.forName("me.jellysquid.mods.sodium.client.render.immediate.model.BakedModelEncoder");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * marks the given Sprite to be animated in this frame
     * @param sprite sprite to mark
     */
    public static void markTextureAsActive(TextureAtlasSprite sprite) {
        if (init()) {
            try {
                // SpriteUtil.markSpriteActive(sprite);
                SpriteUtil_markSpriteActive.invoke(null, sprite);
            } catch (InvocationTargetException | IllegalAccessException e) {
                VRSettings.LOGGER.error("Vivecraft: couldn't set Sodium sprite as animated:", e);
            }
        }
    }

    /**
     * copies vertex info from one ModelPart face to another
     * @param source source ModelPart to copy from
     * @param dest target ModelPart to copy to
     * @param sourcePoly source face to copy from
     * @param destPoly target face to copy to
     */
    public static void copyModelCuboidUV(ModelPart source, ModelPart dest, int sourcePoly, int destPoly) {
        if (init()) {
            try {
                if (HAS_MODELCUBOID_QUADS) {
                    // ModelCuboid stores the texture info in quads per face
                    Object sourceQuad = ((Object[]) ModelCuboid_quads.get(
                        ((Object[]) ModelPart_sodium$cuboids.get(source))[0])
                    )[sourcePoly];
                    Object destQuad = ((Object[]) ModelCuboid_quads.get(
                        ((Object[]) ModelPart_sodium$cuboids.get(dest))[0])
                    )[destPoly];

                    Vector2f[] sourceTextures = (Vector2f[]) ModelCuboid$Quad_textures.get(sourceQuad);
                    Vector2f[] destTextures = (Vector2f[]) ModelCuboid$Quad_textures.get(destQuad);

                    for (int i = 0; i < sourceTextures.length; i++) {
                        destTextures[i].x = sourceTextures[i].x;
                        destTextures[i].y = sourceTextures[i].y;
                    }
                } else if (HAS_MODELCUBOID_FLOATS) {
                    // ModelCuboid stores the texture info in per cube floats
                    Object sourceCuboid = HAS_MODELCUBOID_CUBES ? Cube_sodium$cuboid.get(source.cubes.get(0)) :
                        ((Object[]) ModelPart_sodium$cuboids.get(source))[0];

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

                    Object destCuboid = HAS_MODELCUBOID_CUBES ? Cube_sodium$cuboid.get(dest.cubes.get(0)) :
                        ((Object[]) ModelPart_sodium$cuboids.get(dest))[0];
                    ((ModelCuboidExtension) destCuboid).vivecraft$addOverrides(
                        mapDirection(destPoly),
                        mapDirection(sourcePoly),
                        UVs
                    );
                }
            } catch (IllegalAccessException | ClassCastException e) {
                VRSettings.LOGGER.error(
                    "Vivecraft: sodium version has ModelCuboids, but fields are an unexpected type. VR hands will probably look wrong:", e);
                HAS_MODELCUBOID_FLOATS = false;
                HAS_MODELCUBOID_QUADS = false;
            }
        }
    }

    /**
     * sodium change the internal cube face indices, this maps the pre 0.5 indices to 0.5+ ones
     * @param old pre 0.5 face index
     * @return post 0.5 index
     */
    private static int mapDirection(int old) {
        return switch (old) {
            case 1 -> 2;
            case 2 -> 0;
            case 3 -> 1;
            case 4 -> 3;
            case 5 -> 5;
            default -> 4; // 0 case
        };
    }

    /**
     * initializes all Reflections
     * @return if init was successful
     */
    private static boolean init() {
        if (INITIALIZED) {
            // try to softly fail when something went wrong
            return !INIT_FAILED;
        }
        try {
            Class<?> spriteUtil = getClassWithAlternative(
                "me.jellysquid.mods.sodium.client.render.texture.SpriteUtil",
                "net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil"
            );

            SpriteUtil_markSpriteActive = spriteUtil.getMethod("markSpriteActive", TextureAtlasSprite.class);

            try {
                // model
                Class<?> ModelCuboid = getClassWithAlternative(
                    "me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid",
                    "net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid"
                );

                try {
                    // all cube cuboids are stored in the ModelPart
                    ModelPart_sodium$cuboids = ModelPart.class.getDeclaredField("sodium$cuboids");
                    ModelPart_sodium$cuboids.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                    // cuboid is stored in the Cube directly instead
                    Cube_sodium$cuboid = ModelPart.Cube.class.getDeclaredField("sodium$cuboid");
                    Cube_sodium$cuboid.setAccessible(true);
                    HAS_MODELCUBOID_CUBES = true;

                }
                try {
                    Class<?> ModelCuboid$Quad = getClassWithAlternative(
                        "me.jellysquid.mods.sodium.client.render.immediate.model.ModelCuboid$Quad",
                        "net.caffeinemc.mods.sodium.client.render.immediate.model.ModelCuboid$Quad"
                    );
                    // texture bounds are stored in pre face quads
                    ModelCuboid_quads = ModelCuboid.getDeclaredField("quads");
                    ModelCuboid$Quad_textures = ModelCuboid$Quad.getDeclaredField("textures");
                    HAS_MODELCUBOID_QUADS = true;
                } catch (ClassNotFoundException noQuads) {
                    // texture bounds are stored in global UVs instead
                    ModelCuboid_u0 = ModelCuboid.getDeclaredField("u0");
                    ModelCuboid_u1 = ModelCuboid.getDeclaredField("u1");
                    ModelCuboid_u2 = ModelCuboid.getDeclaredField("u2");
                    ModelCuboid_u3 = ModelCuboid.getDeclaredField("u3");
                    ModelCuboid_u4 = ModelCuboid.getDeclaredField("u4");
                    ModelCuboid_u5 = ModelCuboid.getDeclaredField("u5");
                    ModelCuboid_v0 = ModelCuboid.getDeclaredField("v0");
                    ModelCuboid_v1 = ModelCuboid.getDeclaredField("v1");
                    ModelCuboid_v2 = ModelCuboid.getDeclaredField("v2");
                    HAS_MODELCUBOID_FLOATS = true;
                }
            } catch (ClassNotFoundException ignored) {
                // older versions didn't use that so can ignore it
            } catch (NoSuchFieldException e) {
                VRSettings.LOGGER.error("Vivecraft: sodium version has ModelCuboids, but some fields are not found. VR hands will probably look wrong:", e);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            INIT_FAILED = true;
            VRSettings.LOGGER.error("Vivecraft: Failed to initialize Sodium compat:", e);
        }
        INITIALIZED = true;
        return !INIT_FAILED;
    }

    /**
     * does a class Lookup with an alternative, for convenience, since iris changed packages
     * @param class1 first option
     * @param class2 alternative option
     * @return found class
     * @throws ClassNotFoundException if neither class exists
     */
    private static Class<?> getClassWithAlternative(String class1, String class2) throws ClassNotFoundException {
        try {
            return Class.forName(class1);
        } catch (ClassNotFoundException e) {
            return Class.forName(class2);
        }
    }
}
