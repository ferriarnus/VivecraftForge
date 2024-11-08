package org.vivecraft.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class CommonNetworkHelper {

    public static final ResourceLocation CHANNEL = new ResourceLocation("vivecraft:data");

    // maximum supported network version
    public static final int MAX_SUPPORTED_NETWORK_VERSION = 1;
    // minimum supported network version
    public static final int MIN_SUPPORTED_NETWORK_VERSION = 0;

    public static void serializeF(FriendlyByteBuf buffer, Vector3fc vec3) {
        buffer.writeFloat(vec3.x());
        buffer.writeFloat(vec3.y());
        buffer.writeFloat(vec3.z());
    }

    public static Vector3fc deserializeFVec3(FriendlyByteBuf buffer) {
        return new Vector3f(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public static void serialize(FriendlyByteBuf buffer, Quaternionfc quat) {
        buffer.writeFloat(quat.w());
        buffer.writeFloat(quat.x());
        buffer.writeFloat(quat.y());
        buffer.writeFloat(quat.z());
    }

    public static Quaternionf deserializeVivecraftQuaternion(FriendlyByteBuf buffer) {
        float w = buffer.readFloat();
        return new Quaternionf(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), w);
    }
}
