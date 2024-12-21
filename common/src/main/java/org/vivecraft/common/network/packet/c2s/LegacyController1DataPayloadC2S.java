package org.vivecraft.common.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.common.network.Pose;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * legacy packet, holds the reversed hand flag and the offhand controller pose
 * @param leftHanded if the player has reversed hands set
 * @param offHand pose of the players main controller
 */
public record LegacyController1DataPayloadC2S(boolean leftHanded, Pose offHand) implements VivecraftPayloadC2S {
    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.CONTROLLER1DATA;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        buffer.writeBoolean(this.leftHanded);
        this.offHand.serialize(buffer);
    }

    public static LegacyController1DataPayloadC2S read(FriendlyByteBuf buffer) {
        return new LegacyController1DataPayloadC2S(buffer.readBoolean(), Pose.deserialize(buffer));
    }
}
