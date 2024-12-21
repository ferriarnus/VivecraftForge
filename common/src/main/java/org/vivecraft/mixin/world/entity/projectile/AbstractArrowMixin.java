package org.vivecraft.mixin.world.entity.projectile;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.common.utils.Utils;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;
import org.vivecraft.server.config.ServerConfig;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin extends Entity {

    @Shadow
    private double baseDamage;

    public AbstractArrowMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void vivecraft$startPos(CallbackInfo ci, @Local(argsOnly = true) LivingEntity owner) {
        if (owner instanceof ServerPlayer player) {
            ServerVivePlayer serverVivePlayer = ServerVRPlayers.getVivePlayer(player);
            if (serverVivePlayer != null && serverVivePlayer.isVR()) {
                Vec3 aimPos = serverVivePlayer.getLimbPos(serverVivePlayer.activeLimb);
                Vec3 aimDir = serverVivePlayer.getAimDir();

                this.setPos(aimPos.x + aimDir.x, aimPos.y + aimDir.y, aimPos.z + aimDir.z);
            }
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void vivecraft$damageMultiplier(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (((Projectile) (Object) this).getOwner() instanceof ServerPlayer owner) {
            ServerVivePlayer serverVivePlayer = ServerVRPlayers.getVivePlayer(owner);
            Vec3 hitPos;
            double multiplier = 1.0;
            if ((hitPos = vivecraft$isHeadshot(entityHitResult)) != null) {
                if (serverVivePlayer != null && serverVivePlayer.isVR()) {
                    if (serverVivePlayer.isSeated()) {
                        multiplier = ServerConfig.BOW_SEATED_HEADSHOT_MULTIPLIER.get();
                    } else {
                        multiplier = ServerConfig.BOW_STANDING_HEADSHOT_MULTIPLIER.get();
                    }
                } else {
                    multiplier = ServerConfig.BOW_VANILLA_HEADSHOT_MULTIPLIER.get();
                }

                if (multiplier > 1.0) {
                    // send headshot particles
                    ((ServerLevel) this.level()).sendParticles(
                        owner,
                        ParticleTypes.CRIT,
                        true, // always render the hit particles on the client
                        hitPos.x, hitPos.y, hitPos.z,
                        5,
                        -this.getDeltaMovement().x, -this.getDeltaMovement().y, -this.getDeltaMovement().z,
                        0.1);
                    // send sound effect
                    owner.connection.send(
                        new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ITEM_BREAK),
                            SoundSource.PLAYERS, owner.getX(), owner.getY(), owner.getZ(), 0.7f, 0.5f,
                            owner.level().random.nextLong()));
                }
            }
            // if headshots are disabled, still use the regular multiplier
            if (serverVivePlayer != null && serverVivePlayer.isVR()) {
                if (serverVivePlayer.isSeated()) {
                    multiplier = Math.max(multiplier, ServerConfig.BOW_SEATED_MULTIPLIER.get());
                } else {
                    multiplier = Math.max(multiplier, ServerConfig.BOW_STANDING_MULTIPLIER.get());
                }
            }

            this.baseDamage *= multiplier;
        }
    }

    /**
     * checks if the hit was a headshot, and returns the hit position, if it was, null otherwise
     * @param hit hit result of the original hit
     * @return hit position on the head hit box, if there is one, {@code null} otherwise
     */
    @Unique
    private Vec3 vivecraft$isHeadshot(EntityHitResult hit) {
        AABB headBox;
        if ((headBox = Utils.getEntityHeadHitbox(hit.getEntity(), 0.3)) != null) {
            Vec3 originalHitPos = hit.getEntity()
                .getBoundingBox()
                .clip(this.position(), this.position().add(this.getDeltaMovement().scale(2.0)))
                .orElse(this.position().add(this.getDeltaMovement()));
            return headBox
                .clip(this.position(), originalHitPos)
                .orElse(headBox.contains(this.position()) ? this.position() : null);
        }
        return null;
    }
}
