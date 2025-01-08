package org.vivecraft.client_vr.render;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.resources.ResourceLocation;

public class VRShaders {
    // FSAA shader and its uniforms
    public static ShaderProgram LANCZOS_SHADER = new ShaderProgram(
        ResourceLocation.fromNamespaceAndPath("vivecraft", "core/lanczos_vr"),
        DefaultVertexFormat.POSITION_TEX, ShaderDefines.EMPTY);
    public static AbstractUniform LANCZOS_TEXEL_WIDTH_OFFSET_UNIFORM;
    public static AbstractUniform LANCZOS_TEXEL_HEIGHT_OFFSET_UNIFORM;

    // mixed reality shader and its uniforms
    public static ShaderProgram MIXED_REALITY_SHADER = new ShaderProgram(
        ResourceLocation.fromNamespaceAndPath("vivecraft", "core/mixedreality_vr"),
        DefaultVertexFormat.POSITION_TEX, ShaderDefines.EMPTY);
    public static AbstractUniform MIXED_REALITY_HMD_VIEW_POSITION_UNIFORM;
    public static AbstractUniform MIXED_REALITY_HMD_PLANE_NORMAL_UNIFORM;
    public static AbstractUniform MIXED_REALITY_PROJECTION_MATRIX_UNIFORM;
    public static AbstractUniform MIXED_REALITY_VIEW_MATRIX_UNIFORM;
    public static AbstractUniform MIXED_REALITY_FIRST_PERSON_PASS_UNIFORM;
    public static AbstractUniform MIXED_REALITY_KEY_COLOR_UNIFORM;
    public static AbstractUniform MIXED_REALITY_ALPHA_MODE_UNIFORM;

    // vr post shader and its uniforms
    public static ShaderProgram POST_PROCESSING_SHADER = new ShaderProgram(
        ResourceLocation.fromNamespaceAndPath("vivecraft", "core/postprocessing_vr"),
        DefaultVertexFormat.POSITION_TEX, ShaderDefines.EMPTY);
    public static AbstractUniform POST_PROCESSING_FOV_REDUCTION_RADIUS_UNIFORM;
    public static AbstractUniform POST_PROCESSING_FOV_REDUCTION_OFFSET_UNIFORM;
    public static AbstractUniform POST_PROCESSING_FOV_REDUCTION_BORDER_UNIFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_HEALTH_ALPHA_UNiFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_FREEZE_ALPHA_UNiFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_WATER_AMPLITUDE_UNIFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_PORTAL_AMPLITUDE_UNIFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_PUMPKIN_AMPLITUDE_UNIFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_TIME_UNIFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_BLACK_ALPHA_UNIFORM;
    public static AbstractUniform POST_PROCESSING_OVERLAY_EYE_UNIFORM;

    // end portal shaders
    public static ShaderProgram RENDERTYPE_END_PORTAL_VR_SHADER = new ShaderProgram(
        ResourceLocation.fromNamespaceAndPath("vivecraft", "core/rendertype_end_portal_vr"),
        DefaultVertexFormat.POSITION, ShaderDefines.EMPTY);
    public static ShaderProgram RENDERTYPE_END_GATEWAY_VR_SHADER = new ShaderProgram(
        ResourceLocation.fromNamespaceAndPath("vivecraft", "core/rendertype_end_gateway_vr"),
        DefaultVertexFormat.POSITION, ShaderDefines.EMPTY);

    private VRShaders() {}

    public static void setupDepthMask() {
        CompiledShaderProgram program = Minecraft.getInstance().getShaderManager().getProgram(MIXED_REALITY_SHADER);
        MIXED_REALITY_HMD_VIEW_POSITION_UNIFORM = program.safeGetUniform("hmdViewPosition");
        MIXED_REALITY_HMD_PLANE_NORMAL_UNIFORM = program.safeGetUniform("hmdPlaneNormal");
        MIXED_REALITY_PROJECTION_MATRIX_UNIFORM = program.safeGetUniform("projectionMatrix");
        MIXED_REALITY_VIEW_MATRIX_UNIFORM = program.safeGetUniform("viewMatrix");
        MIXED_REALITY_FIRST_PERSON_PASS_UNIFORM = program.safeGetUniform("firstPersonPass");
        MIXED_REALITY_KEY_COLOR_UNIFORM = program.safeGetUniform("keyColor");
        MIXED_REALITY_ALPHA_MODE_UNIFORM = program.safeGetUniform("alphaMode");
    }

    public static void setupFSAA() {
        CompiledShaderProgram program = Minecraft.getInstance().getShaderManager().getProgram(LANCZOS_SHADER);
        LANCZOS_TEXEL_WIDTH_OFFSET_UNIFORM = program.safeGetUniform("texelWidthOffset");
        LANCZOS_TEXEL_HEIGHT_OFFSET_UNIFORM = program.safeGetUniform("texelHeightOffset");
    }

    public static void setupFOVReduction() {
        CompiledShaderProgram program = Minecraft.getInstance().getShaderManager().getProgram(POST_PROCESSING_SHADER);
        POST_PROCESSING_FOV_REDUCTION_RADIUS_UNIFORM = program.safeGetUniform("circle_radius");
        POST_PROCESSING_FOV_REDUCTION_OFFSET_UNIFORM = program.safeGetUniform("circle_offset");
        POST_PROCESSING_FOV_REDUCTION_BORDER_UNIFORM = program.safeGetUniform("border");
        POST_PROCESSING_OVERLAY_HEALTH_ALPHA_UNiFORM = program.safeGetUniform("redalpha");
        POST_PROCESSING_OVERLAY_FREEZE_ALPHA_UNiFORM = program.safeGetUniform("bluealpha");
        POST_PROCESSING_OVERLAY_WATER_AMPLITUDE_UNIFORM = program.safeGetUniform("water");
        POST_PROCESSING_OVERLAY_PORTAL_AMPLITUDE_UNIFORM = program.safeGetUniform("portal");
        POST_PROCESSING_OVERLAY_PUMPKIN_AMPLITUDE_UNIFORM = program.safeGetUniform("pumpkin");
        POST_PROCESSING_OVERLAY_EYE_UNIFORM = program.safeGetUniform("eye");
        POST_PROCESSING_OVERLAY_TIME_UNIFORM = program.safeGetUniform("portaltime");
        POST_PROCESSING_OVERLAY_BLACK_ALPHA_UNIFORM = program.safeGetUniform("blackalpha");
    }
}
