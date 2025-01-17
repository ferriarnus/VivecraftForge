package org.vivecraft.common.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.common.network.CommonNetworkHelper;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.FBTMode;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * holds the clients current active BodyPart, this is the BodyPart that caused the next action
 *
 * @param bodyPart the active BodyPart
 */
public record ActiveBodyPartPayloadC2S(VRBodyPart bodyPart) implements VivecraftPayloadC2S {

    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.ACTIVEHAND;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        if (ClientNetworking.USED_NETWORK_VERSION < CommonNetworkHelper.NETWORK_VERSION_DUAL_WIELDING &&
            !this.bodyPart.availableInMode(FBTMode.ARMS_ONLY))
        {
            // old plugins only support main and offhand
            buffer.writeByte(VRBodyPart.MAIN_HAND.ordinal());
        } else {
            buffer.writeByte(this.bodyPart.ordinal());
        }
    }

    public static ActiveBodyPartPayloadC2S read(FriendlyByteBuf buffer) {
        return new ActiveBodyPartPayloadC2S(VRBodyPart.values()[buffer.readByte()]);
    }
}
