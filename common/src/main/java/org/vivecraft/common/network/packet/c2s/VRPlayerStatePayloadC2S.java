package org.vivecraft.common.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * holds the clients complete vr state
 * @param playerState vr state of the player
 */
public record VRPlayerStatePayloadC2S(VrPlayerState playerState) implements VivecraftPayloadC2S {
    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.VR_PLAYER_STATE;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        this.playerState.serialize(buffer);
    }

    public static VRPlayerStatePayloadC2S read(FriendlyByteBuf buffer) {
        return new VRPlayerStatePayloadC2S(VrPlayerState.deserialize(buffer, 0));
    }
}
