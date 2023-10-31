package org.vivecraft.mixin.accessor.client.model;

import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerModel.class)
public interface PlayerModelAccessor {
    @Accessor
    boolean isSlim();
}
