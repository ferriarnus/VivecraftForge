package org.vivecraft.common.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * indicates that the server supports roomscale crawling
 */
public record DualWieldingPayloadS2C(boolean allowed) implements VivecraftPayloadS2C {

    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.DUAL_WIELDING;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        buffer.writeBoolean(this.allowed);
    }

    public static DualWieldingPayloadS2C read(FriendlyByteBuf buffer) {
        return new DualWieldingPayloadS2C(buffer.readBoolean());
    }
}
