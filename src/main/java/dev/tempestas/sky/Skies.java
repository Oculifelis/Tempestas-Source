package dev.tempestas.sky;

import com.fundamentum.climate.Season;
import com.fundamentum.climate.Seasons;
import com.fundamentum.data.DataTable;
import com.fundamentum.data.DataTables;
import com.fundamentum.recipeview.RecipeViews;
import dev.tempestas.Tempestas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The sky table, synced so the journal and the item tint know every sky, caught or not. */
public final class Skies {
    public static final DataTable<Sky> TABLE = DataTables
            .register(Tempestas.id("skies"), "tempestas/skies", Sky::parse)
            .synced()
            .onLoad(t -> RecipeViews.refresh());

    public static void init() {
    }

    /** Skies by priority (highest first), then id: the order they are tried in and the journal's page order. */
    public static List<Map.Entry<Identifier, Sky>> sorted() {
        return TABLE.entries().entrySet().stream()
                .sorted(Comparator.<Map.Entry<Identifier, Sky>>comparingInt(e -> -e.getValue().priority())
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
    }

    /** The sky over this spot right now, or null when a jar would come back empty. */
    public static Map.Entry<Identifier, Sky> at(ServerLevel level, BlockPos pos) {
        for (Map.Entry<Identifier, Sky> e : sorted()) {
            if (e.getValue().matches(level, pos)) {
                return e;
            }
        }
        return null;
    }

    /** Where, when and in what season a sky was caught, written into the jar and the journal. */
    public static CaughtSky record(ServerLevel level, BlockPos pos, Identifier sky) {
        long time = level.getDefaultClockTime();
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
        Season season = Seasons.current(level);
        return new CaughtSky(sky, biome, season == null ? "" : season.id().toString(),
                (int) (Math.floorDiv(time, 24000L) + 1), band(time));
    }

    /** The part of the day a clock time falls in, as a word: dawn, morning, noon, afternoon, dusk, night, midnight. */
    public static String band(long time) {
        int t = (int) Math.floorMod(time, 24000L);
        if (t >= 23000 || t < 1000) {
            return "dawn";
        }
        if (t < 5000) {
            return "morning";
        }
        if (t < 7000) {
            return "noon";
        }
        if (t < 11500) {
            return "afternoon";
        }
        if (t < 13500) {
            return "dusk";
        }
        if (t >= 17000 && t < 19000) {
            return "midnight";
        }
        return "night";
    }

    public static Component name(Identifier sky) {
        return Component.translatableWithFallback("sky." + sky.getNamespace() + "." + sky.getPath(), words(sky.getPath()));
    }

    public static Component hint(Identifier sky) {
        return Component.translatableWithFallback("sky." + sky.getNamespace() + "." + sky.getPath() + ".hint", "...");
    }

    /** "Caught in Plains, autumn, day 12, at dusk", with the parts that are known. */
    public static Component provenance(CaughtSky c) {
        Component where = c.biome().isEmpty() ? Component.literal("?") : biomeName(c.biome());
        Component when = Component.translatable("time.tempestas." + c.band());
        if (c.season().isEmpty()) {
            return Component.translatable("sky.tempestas.caught", where, c.day(), when);
        }
        // Item data can come from commands or other mods, so a bad id shows as plain text instead of throwing.
        Identifier s = Identifier.tryParse(c.season());
        if (s == null) {
            return Component.translatable("sky.tempestas.caught_season", where, Component.literal(c.season()), c.day(), when);
        }
        Component season = Component.translatableWithFallback("season." + s.getNamespace() + "." + s.getPath(),
                words(s.getPath()).toLowerCase(Locale.ROOT));
        return Component.translatable("sky.tempestas.caught_season", where, season, c.day(), when);
    }

    private static Component biomeName(String id) {
        Identifier b = Identifier.tryParse(id);
        if (b == null) {
            return Component.literal(id);
        }
        return Component.translatableWithFallback("biome." + b.getNamespace() + "." + b.getPath(), words(b.getPath()));
    }

    private static String words(String path) {
        StringBuilder sb = new StringBuilder();
        for (String w : path.substring(path.lastIndexOf('/') + 1).split("_")) {
            if (!w.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    private Skies() {
    }
}
