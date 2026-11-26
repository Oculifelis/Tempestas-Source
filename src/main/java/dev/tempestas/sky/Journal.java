package dev.tempestas.sky;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** A player's sky journal: the first catch of every sky they have caught. Kept through death, synced to its owner. */
public record Journal(Map<Identifier, CaughtSky> firsts) {
    public static final Journal EMPTY = new Journal(Map.of());

    public static final Codec<Journal> CODEC = Codec.unboundedMap(Identifier.CODEC, CaughtSky.CODEC)
            .xmap(m -> new Journal(Map.copyOf(m)), Journal::firsts);

    public static final StreamCodec<ByteBuf, Journal> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, CaughtSky.STREAM_CODEC)
                    .map(m -> new Journal(Map.copyOf(m)), j -> new HashMap<>(j.firsts()));

    public boolean has(Identifier sky) {
        return firsts.containsKey(sky);
    }

    public Journal with(CaughtSky caught) {
        Map<Identifier, CaughtSky> m = new HashMap<>(firsts);
        m.putIfAbsent(caught.sky(), caught);
        return new Journal(Map.copyOf(m));
    }
}
