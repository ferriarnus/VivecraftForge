package org.vivecraft.common.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.common.network.packet.PayloadIdentifier;

import java.util.UUID;

/**
 * holds a players data
 * @param playerID UUID of the player this data is for
 * @param state vr state of the player
 * @param worldScale world scale of the player
 * @param heightScale calibrated height scale of the player
 */
public record UberPacketPayloadS2C(UUID playerID, VrPlayerState state, float worldScale,
                                   float heightScale) implements VivecraftPayloadS2C
{

    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.UBERPACKET;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        buffer.writeUUID(this.playerID);
        this.state.serialize(buffer);
        buffer.writeFloat(this.worldScale);
        buffer.writeFloat(this.heightScale);
    }

    public static UberPacketPayloadS2C read(FriendlyByteBuf buffer) {
        return new UberPacketPayloadS2C(
            buffer.readUUID(),
            VrPlayerState.deserialize(buffer, 8),
            buffer.readFloat(),
            buffer.readFloat());
    }
}
