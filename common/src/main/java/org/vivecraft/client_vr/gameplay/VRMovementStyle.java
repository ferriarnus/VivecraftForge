package org.vivecraft.client_vr.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class VRMovementStyle {

    private static final ResourceLocation BEAM_PNG = new ResourceLocation("textures/entity/endercrystal/endercrystal_beam.png");

    public String name;
    public boolean cameraSlide;
    public boolean airSparkles;
    public boolean destinationSparkles;
    public boolean showBeam;
    public boolean beamWave;
    public boolean beamArc;
    public boolean beamSpiral;
    public boolean beamGrow;
    public boolean renderVerticalStrip;
    public float beamHalfWidth;
    public float beamSegmentLength;
    public float beamSpiralRadius;
    public int beamVStrips;
    public float textureScrollSpeed;
    public ResourceLocation texture;
    public SoundEvent startTeleportingSound;
    public float startTeleportingSoundVolume;
    public SoundEvent endTeleportingSound;
    public float endTeleportingSoundVolume;
    public boolean teleportOnRelease;
    public boolean arcAiming;

    public VRMovementStyle() {
        this.setStyle("Arc");
    }

    public void setStyle(String requestedStyle) {
        boolean changedStyle = true;

        if ("Minimal".equals(requestedStyle)) {
            this.name = requestedStyle;
            this.cameraSlide = false;
            this.airSparkles = true;
            this.destinationSparkles = true;
            this.showBeam = false;
            this.startTeleportingSound = null;
            this.endTeleportingSoundVolume = 0.8F;
            this.endTeleportingSound = SoundEvents.ENDERMAN_TELEPORT;
            this.teleportOnRelease = false;
            this.arcAiming = false;
        } else if ("Beam".equals(requestedStyle)) {
            this.name = requestedStyle;
            this.cameraSlide = false;
            this.airSparkles = true;
            this.destinationSparkles = true;
            this.showBeam = true;
            this.beamWave = false;
            this.beamArc = false;
            this.beamSpiral = false;
            this.beamGrow = true;
            this.beamHalfWidth = 0.1F;
            this.beamSegmentLength = 0.1F;
            this.beamVStrips = 16;
            this.renderVerticalStrip = true;
            this.textureScrollSpeed = 3.0F;
            this.texture = BEAM_PNG;
            this.startTeleportingSound = null;
            this.endTeleportingSoundVolume = 0.8F;
            this.endTeleportingSound = SoundEvents.ENDERMAN_TELEPORT;
            this.teleportOnRelease = false;
            this.arcAiming = false;
        } else if ("Tunnel".equals(requestedStyle)) {
            this.name = requestedStyle;
            this.cameraSlide = false;
            this.airSparkles = true;
            this.destinationSparkles = true;
            this.showBeam = true;
            this.beamWave = false;
            this.beamArc = false;
            this.beamSpiral = true;
            this.beamGrow = true;
            this.beamHalfWidth = 0.1F;
            this.beamSpiralRadius = 1.6F;
            this.renderVerticalStrip = true;
            this.beamVStrips = 16;
            this.textureScrollSpeed = 3.0F;
            this.texture = BEAM_PNG;
            this.startTeleportingSound = null;
            this.endTeleportingSoundVolume = 0.8F;
            this.endTeleportingSound = SoundEvents.ENDERMAN_TELEPORT;
            this.teleportOnRelease = false;
            this.arcAiming = false;
        } else if ("Grapple".equals(requestedStyle)) {
            this.name = requestedStyle;
            this.cameraSlide = true;
            this.airSparkles = false;
            this.destinationSparkles = true;
            this.showBeam = true;
            this.beamWave = true;
            this.beamArc = false;
            this.beamSpiral = false;
            this.beamGrow = true;
            this.beamHalfWidth = 0.05F;
            this.beamSegmentLength = 0.05F;
            this.renderVerticalStrip = false;
            this.beamVStrips = 2;
            this.textureScrollSpeed = 7.0F;
            this.texture = BEAM_PNG;
            this.startTeleportingSoundVolume = 0.5F;
            this.endTeleportingSoundVolume = 0.5F;
            this.startTeleportingSound = null;
            this.endTeleportingSound = SoundEvents.ENDERMAN_TELEPORT;
            this.teleportOnRelease = false;
            this.arcAiming = false;
        } else if ("Arc".equals(requestedStyle)) {
            this.name = requestedStyle;
            this.cameraSlide = false;
            this.airSparkles = false;
            this.destinationSparkles = false;
            this.showBeam = true;
            this.beamWave = false;
            this.beamArc = false;
            this.beamSpiral = false;
            this.beamGrow = false;
            this.beamHalfWidth = 0.1F;
            this.beamVStrips = 1;
            this.renderVerticalStrip = true;
            this.textureScrollSpeed = 3.0F;
            this.texture = BEAM_PNG;
            this.startTeleportingSound = null;
            this.endTeleportingSoundVolume = 0.7F;
            this.endTeleportingSound = null;
            this.teleportOnRelease = true;
            this.arcAiming = true;
        } else {
            changedStyle = false;
            if (Minecraft.getInstance().gui != null) {
                Minecraft.getInstance().gui.getChat()
                    .addMessage(Component.literal("Unknown teleport style requested: " + requestedStyle));
            }
        }

        if (changedStyle && Minecraft.getInstance().gui != null) {
            Minecraft.getInstance().gui.getChat()
                .addMessage(Component.literal("Teleport style (RCTRL-M): " + this.name));
        }
    }
}
