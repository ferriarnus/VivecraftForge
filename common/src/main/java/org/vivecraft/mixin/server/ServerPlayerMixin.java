package org.vivecraft.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.mixin.world.entity.PlayerMixin;
import org.vivecraft.server.ServerNetworking;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;
import org.vivecraft.server.config.ServerConfig;

import java.util.IllegalFormatException;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends PlayerMixin {

    @Shadow
    @Final
    public MinecraftServer server;

    protected ServerPlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "initInventoryMenu", at = @At("TAIL"))
    private void vivecraft$addItemEasterEgg(CallbackInfo ci) {
        // triggers on player respawn and rejoin
        ServerVivePlayer serverVivePlayer = vivecraft$getVivePlayer();
        if (ServerConfig.VR_FUN.get() && serverVivePlayer != null && serverVivePlayer.isVR() &&
            this.random.nextInt(40) == 3)
        {
            ItemStack easterEggItem;
            if (this.random.nextInt(2) == 1) {
                easterEggItem = new ItemStack(Items.PUMPKIN_PIE)
                    .setHoverName(Component.literal("EAT ME"));
            } else {
                easterEggItem = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER)
                    .setHoverName(Component.literal("DRINK ME"));
            }

            easterEggItem.getOrCreateTag().putInt("HideFlags", 32);

            if (this.getInventory().add(easterEggItem)) {
                this.inventoryMenu.broadcastChanges();
            }
        }
    }

    @Inject(method = "doTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;tick()V", shift = Shift.AFTER))
    private void vivecraft$overridePose(CallbackInfo ci) {
        ServerVRPlayers.overridePose((ServerPlayer) (Object) this);
    }

    /**
     * inject into {@link Player#sweepAttack}
     */
    @Override
    protected int vivecraft$modifySweepParticleSpawnPos(
        ServerLevel instance, ParticleOptions type, double posX, double posY, double posZ, int particleCount,
        double xOffset, double yOffset, double zOffset, double speed, Operation<Integer> original)
    {
        ServerVivePlayer serverVivePlayer = vivecraft$getVivePlayer();
        if (serverVivePlayer != null && serverVivePlayer.isVR()) {
            Vec3 aim = serverVivePlayer.getLimbDir(serverVivePlayer.activeLimb);
            float yaw = (float) Math.atan2(-aim.x, aim.z);

            xOffset = -Mth.sin(yaw);
            zOffset = Mth.cos(yaw);

            Vec3 pos = serverVivePlayer.getLimbPos(serverVivePlayer.activeLimb);

            return original.call(instance, type,
                pos.x + xOffset, pos.y, pos.z + zOffset,
                particleCount,
                xOffset, yOffset, zOffset, speed);
        } else {
            return original.call(instance, type, posX, posY, posZ, particleCount, xOffset, yOffset, zOffset, speed);
        }
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z")
    )
    private void vivecraft$dropVive(
        ItemStack droppedItem, boolean dropAround, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir,
        @Local ItemEntity item)
    {
        ServerVivePlayer serverVivePlayer = vivecraft$getVivePlayer();
        if (!dropAround && serverVivePlayer != null && serverVivePlayer.isVR()) {
            // spawn item from players hand
            Vec3 pos = serverVivePlayer.getLimbPos(serverVivePlayer.activeLimb);
            Vec3 aim = serverVivePlayer.getLimbDir(serverVivePlayer.activeLimb);

            // item speed, taken from Player#drop
            final float speed = 0.3F;
            item.setDeltaMovement(aim.x * speed, aim.y * speed, aim.z * speed);
            item.setPos(pos.x + item.getDeltaMovement().x,
                pos.y + item.getDeltaMovement().y,
                pos.z + item.getDeltaMovement().z);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void vivecraft$checkCanGetHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = source.getEntity();
        ServerPlayer other = null;

        // check if the damage came from another player
        if (entity instanceof ServerPlayer) {
            other = (ServerPlayer) entity;
        } else if ((entity instanceof AbstractArrow && (((AbstractArrow) entity).getOwner() instanceof ServerPlayer))) {
            other = (ServerPlayer) ((AbstractArrow) entity).getOwner();
        }

        if (other != null) {
            // both entities are players, so need to check

            ServerVivePlayer otherVive = ServerVRPlayers.getVivePlayer(other);
            ServerVivePlayer thisVive = vivecraft$getVivePlayer();

            // create new object, if they are null, simplifies the checks
            if (otherVive == null) {
                otherVive = new ServerVivePlayer(other);
            }

            if (thisVive == null) {
                thisVive = new ServerVivePlayer((ServerPlayer) (Object) this);
            }

            boolean blockedDamage = false;
            String blockedDamageCase = "";

            if ((!otherVive.isVR() && thisVive.isVR() && thisVive.isSeated()) ||
                (!thisVive.isVR() && otherVive.isVR() && otherVive.isSeated()))
            {
                // nonvr vs Seated
                if (!ServerConfig.PVP_SEATEDVR_VS_NONVR.get()) {
                    blockedDamage = true;
                    blockedDamageCase = "canceled nonvr vs seated VR damage";
                }
            } else if ((!otherVive.isVR() && thisVive.isVR() && !thisVive.isSeated()) ||
                (!thisVive.isVR() && otherVive.isVR() && !otherVive.isSeated()))
            {
                // nonvr vs Standing
                if (!ServerConfig.PVP_VR_VS_NONVR.get()) {
                    blockedDamage = true;
                    blockedDamageCase = "canceled nonvr vs standing VR damage";
                }
            } else if ((otherVive.isVR() && otherVive.isSeated() && thisVive.isVR() && !thisVive.isSeated()) ||
                (thisVive.isVR() && thisVive.isSeated() && otherVive.isVR() && !otherVive.isSeated()))
            {
                // Standing vs Seated
                if (!ServerConfig.PVP_VR_VS_SEATEDVR.get()) {
                    blockedDamage = true;
                    blockedDamageCase = "canceled seated VR vs standing VR damage";
                }
            } else if (otherVive.isVR() && !otherVive.isSeated() && thisVive.isVR() && !thisVive.isSeated()) {
                // Standing vs Standing
                if (!ServerConfig.PVP_VR_VS_VR.get()) {
                    blockedDamage = true;
                    blockedDamageCase = "canceled standing VR vs standing VR damage";
                }
            } else if (otherVive.isVR() && otherVive.isSeated() && thisVive.isVR() && thisVive.isSeated()) {
                // Seated vs Seated
                if (!ServerConfig.PVP_SEATEDVR_VS_SEATEDVR.get()) {
                    blockedDamage = true;
                    blockedDamageCase = "canceled seated VR vs seated VR damage";
                }
            }
            if (blockedDamage) {
                if (ServerConfig.PVP_NOTIFY_BLOCKED_DAMAGE.get()) {
                    other.sendSystemMessage(Component.literal(blockedDamageCase));
                }
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void vivecraft$customDeathMessage(DamageSource damageSource, CallbackInfo ci) {
        // only when enabled
        if (ServerConfig.MESSAGES_ENABLED.get()) {
            ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer((ServerPlayer) (Object) this);
            String message = "";
            String entity = "";

            // get the right message
            if (damageSource.getEntity() != null) {
                entity = damageSource.getEntity().getName().plainCopy().getString();
                // death by mob
                if (vivePlayer == null) {
                    message = ServerConfig.MESSAGES_DEATH_BY_MOB_VANILLA.get();
                } else if (!vivePlayer.isVR()) {
                    message = ServerConfig.MESSAGES_DEATH_BY_MOB_NONVR.get();
                } else if (vivePlayer.isSeated()) {
                    message = ServerConfig.MESSAGES_DEATH_BY_MOB_SEATED.get();
                } else {
                    message = ServerConfig.MESSAGES_DEATH_BY_MOB_VR.get();
                }
            }

            if (message.isEmpty()) {
                // general death, of if the mob one isn't set
                if (vivePlayer == null) {
                    message = ServerConfig.MESSAGES_DEATH_VANILLA.get();
                } else if (!vivePlayer.isVR()) {
                    message = ServerConfig.MESSAGES_DEATH_NONVR.get();
                } else if (vivePlayer.isSeated()) {
                    message = ServerConfig.MESSAGES_DEATH_SEATED.get();
                } else {
                    message = ServerConfig.MESSAGES_DEATH_VR.get();
                }
            }

            // actually send the message, if there is one set
            if (!message.isEmpty()) {
                try {
                    this.server.getPlayerList().broadcastSystemMessage(Component.literal(message.formatted(getName().getString(), entity)), false);
                } catch (IllegalFormatException e) {
                    // catch errors users might put into the messages, to not crash other stuff
                    ServerNetworking.LOGGER.error("Vivecraft: Death message '{}' has errors:", message, e);
                }
            }
        }
    }

    @Unique
    private ServerVivePlayer vivecraft$getVivePlayer() {
        return ServerVRPlayers.getVivePlayer((ServerPlayer) (Object) this);
    }
}
