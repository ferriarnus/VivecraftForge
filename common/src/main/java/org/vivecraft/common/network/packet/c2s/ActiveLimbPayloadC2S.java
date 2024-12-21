package org.vivecraft.common.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.common.network.Limb;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * holds the clients current active limb, this is the limb that caused the next action
 * @param limb the active limb
 */
public record ActiveLimbPayloadC2S(Limb limb) implements VivecraftPayloadC2S {

    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.ACTIVEHAND;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        if (ClientNetworking.USED_NETWORK_VERSION < CommonNetworkHelper.NETWORK_VERSION_DUAL_WIELDING &&
            this.limb.ordinal() >= 1)
        {
            // old plugins only support main and offhand
            buffer.writeByte(0);
        } else {
            buffer.writeByte(this.limb.ordinal());
        }
    }

    public static ActiveLimbPayloadC2S read(FriendlyByteBuf buffer) {
        return new ActiveLimbPayloadC2S(Limb.values()[buffer.readByte()]);
    }
}
