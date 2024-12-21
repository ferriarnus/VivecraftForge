package org.vivecraft.mixin.world.entity;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.common.network.Limb;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;
import org.vivecraft.server.config.ServerConfig;

@Mixin(Inventory.class)
public class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Shadow
    @Final
    public NonNullList<ItemStack> offhand;

    @ModifyReturnValue(method = "getSelected", at = @At("RETURN"))
    private ItemStack vivecraft$dualHandingItem(ItemStack original) {
        return vivecraft$activeItem(original);
    }

    @WrapOperation(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float vivecraft$dualHandingDestroySpeed(ItemStack instance, BlockState state, Operation<Float> original) {
        return original.call(vivecraft$activeItem(instance), state);
    }

    @Unique
    private ItemStack vivecraft$activeItem(ItemStack original) {
        Limb limb = null;
        // server side
        if (this.player instanceof ServerPlayer serverPlayer && ServerConfig.DUAL_WIELDING.get()) {
            if (ServerVRPlayers.isVRPlayer(serverPlayer)) {
                ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
                // older clients don't reset the active hand
                if (vivePlayer.networkVersion >= CommonNetworkHelper.NETWORK_VERSION_DUAL_WIELDING) {
                    limb = vivePlayer.activeLimb;
                }
            }
        }
        // client side
        else if (this.player.isLocalPlayer() && VRState.VR_RUNNING && ClientNetworking.SERVER_ALLOWS_DUAL_WIELDING)
        {
            limb = ClientNetworking.LAST_SENT_LIMB;
        }

        if (limb != null) {
            if (limb == Limb.OFF_HAND) {
                return this.offhand.get(0);
            } else if (limb != Limb.MAIN_HAND) {
                // feet
                return ItemStack.EMPTY;
            }
        }
        return original;
    }
}
