package dev.tempestas.net;

import dev.tempestas.Tempestas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The clouds over released patches near a player. The server sends it when a jar is opened and every second while the
 * patch lasts, so a player who walks up or logs in late still gets it; the client draws the clouds itself.
 */
public record CloudsPayload(List<Entry> clouds) implements CustomPacketPayload {
    public static final Type<CloudsPayload> TYPE = new Type<>(Tempestas.id("release_clouds"));
    public static final StreamCodec<FriendlyByteBuf, CloudsPayload> CODEC = CustomPacketPayload.codec(CloudsPayload::write, CloudsPayload::read);

    /** Set by the client half; the dedicated server never receives this payload. */
    public static Consumer<CloudsPayload> receiver = p -> { };

    /** One cloud: the patch centre, its radius, the cloud's shape, and how far through its life it is. */
    public record Entry(int x, int y, int z, int radius, int height, int spread, int thickness, float darkness, int left, int duration) {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) ->
                e.registrar("1").playToClient(TYPE, CODEC, (payload, ctx) -> receiver.accept(payload)));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(clouds.size());
        for (Entry c : clouds) {
            buf.writeInt(c.x());
            buf.writeInt(c.y());
            buf.writeInt(c.z());
            buf.writeVarInt(c.radius());
            buf.writeVarInt(c.height());
            buf.writeVarInt(c.spread());
            buf.writeVarInt(c.thickness());
            buf.writeFloat(c.darkness());
            buf.writeVarInt(c.left());
            buf.writeVarInt(c.duration());
        }
    }

    private static CloudsPayload read(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Entry(buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));
        }
        return new CloudsPayload(List.copyOf(out));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
