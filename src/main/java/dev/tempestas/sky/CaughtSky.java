package dev.tempestas.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** A caught sky and where it came from: the jar's data component, and one page line of the journal. */
public record CaughtSky(Identifier sky, String biome, String season, int day, String band) {
    public static final Codec<CaughtSky> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("sky").forGetter(CaughtSky::sky),
            Codec.STRING.optionalFieldOf("biome", "").forGetter(CaughtSky::biome),
            Codec.STRING.optionalFieldOf("season", "").forGetter(CaughtSky::season),
            Codec.INT.optionalFieldOf("day", 1).forGetter(CaughtSky::day),
            Codec.STRING.optionalFieldOf("band", "noon").forGetter(CaughtSky::band)
    ).apply(i, CaughtSky::new));

    public static final StreamCodec<ByteBuf, CaughtSky> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CaughtSky::sky,
            ByteBufCodecs.STRING_UTF8, CaughtSky::biome,
            ByteBufCodecs.STRING_UTF8, CaughtSky::season,
            ByteBufCodecs.VAR_INT, CaughtSky::day,
            ByteBufCodecs.STRING_UTF8, CaughtSky::band,
            CaughtSky::new);
}
