package dev.tempestas.sky;

import com.fundamentum.climate.Climate;
import com.fundamentum.climate.Season;
import com.fundamentum.climate.Seasons;
import com.fundamentum.moon.Moons;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One entry of {@code data/<ns>/tempestas/skies/<name>.json}: a named sky a Sky Jar can catch, the conditions it
 * needs, and what it does when let out. Every condition is optional; ranges are inclusive and a missing bound is open.
 */
public record Sky(int priority, int color, List<String> biomes, String dimension, boolean needsSky,
                  double minTemperature, double maxTemperature, double minHumidity, double maxHumidity,
                  Weather weather, int timeFrom, int timeTo, List<Integer> moonPhases, String moon,
                  int minY, int maxY, List<String> seasons, long minAge, long maxAge,
                  Forecast.State after, Forecast.State coming, int comingWithin, Release release) {

    public enum Weather {
        ANY, CLEAR, RAIN, SNOW, THUNDER, DRY_THUNDER, THUNDERSNOW;

        static Weather parse(String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "clear" -> CLEAR;
                case "rain" -> RAIN;
                case "snow" -> SNOW;
                case "thunder" -> THUNDER;
                case "dry_thunder" -> DRY_THUNDER;
                case "thundersnow" -> THUNDERSNOW;
                default -> ANY;
            };
        }
    }

    /**
     * What happens when the jar is opened: a patch of this sky for {@code duration} ticks over {@code radius} blocks.
     * {@code particles} drift around the patch, {@code cloud} hangs over it (drawn on the client), and {@code falling}
     * come down from under the cloud at {@code fallingRate} a tick.
     */
    public record Release(int duration, int radius, List<String> particles, Identifier sound, List<SkyEffect> effects,
                          Cloud cloud, List<String> falling, int fallingRate) {
        static Release parse(JsonObject o) {
            List<SkyEffect> effects = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "effects", new JsonArray())) {
                effects.add(SkyEffect.parse(e.getAsJsonObject()));
            }
            String sound = GsonHelper.getAsString(o, "sound", "");
            return new Release(Math.max(20, GsonHelper.getAsInt(o, "duration", 600)),
                    Math.max(1, Math.min(32, GsonHelper.getAsInt(o, "radius", 6))), ids(o, "particles"),
                    sound.isEmpty() ? null : Identifier.parse(sound), List.copyOf(effects),
                    o.has("cloud") ? Cloud.parse(GsonHelper.getAsJsonObject(o, "cloud")) : null,
                    ids(o, "falling"), Math.max(0, Math.min(64, GsonHelper.getAsInt(o, "falling_rate", 20))));
        }

        /** How high rain and snow start: the cloud base, or 8 blocks up without a cloud. */
        public int ceiling() {
            return cloud == null ? 8 : cloud.height();
        }

        private static List<String> ids(JsonObject o, String key) {
            List<String> out = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(o, key, new JsonArray())) {
                out.add(e.getAsString());
            }
            return List.copyOf(out);
        }
    }

    /**
     * The cloud over a released patch: its base {@code height} blocks up, how far past the patch it reaches
     * ({@code spread}), how thick it is, and how dark (0 white, 1 storm grey).
     */
    public record Cloud(int height, int spread, int thickness, float darkness) {
        static Cloud parse(JsonObject o) {
            return new Cloud(Math.max(3, Math.min(24, GsonHelper.getAsInt(o, "height", 8))),
                    Math.max(0, Math.min(16, GsonHelper.getAsInt(o, "spread", 4))),
                    Math.max(1, Math.min(12, GsonHelper.getAsInt(o, "thickness", 4))),
                    Math.max(0.0F, Math.min(1.0F, GsonHelper.getAsFloat(o, "darkness", 0.0F))));
        }
    }

    public static Sky parse(JsonObject json) {
        JsonObject t = GsonHelper.getAsJsonObject(json, "temperature", new JsonObject());
        JsonObject h = GsonHelper.getAsJsonObject(json, "humidity", new JsonObject());
        JsonObject y = GsonHelper.getAsJsonObject(json, "y", new JsonObject());
        JsonObject time = GsonHelper.getAsJsonObject(json, "time", new JsonObject());
        JsonObject age = GsonHelper.getAsJsonObject(json, "weather_age", new JsonObject());
        JsonObject coming = GsonHelper.getAsJsonObject(json, "coming", new JsonObject());
        String colour = GsonHelper.getAsString(json, "color", "ffffff").replace("#", "");
        List<Integer> phases = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "moon_phases", new JsonArray())) {
            phases.add(e.getAsInt());
        }
        return new Sky(
                GsonHelper.getAsInt(json, "priority", 0),
                Integer.parseInt(colour, 16) & 0xFFFFFF,
                biomes(json),
                GsonHelper.getAsString(json, "dimension", ""),
                GsonHelper.getAsBoolean(json, "sky", true),
                GsonHelper.getAsDouble(t, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(t, "max", Double.POSITIVE_INFINITY),
                GsonHelper.getAsDouble(h, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(h, "max", Double.POSITIVE_INFINITY),
                Weather.parse(GsonHelper.getAsString(json, "weather", "any")),
                GsonHelper.getAsInt(time, "from", -1),
                GsonHelper.getAsInt(time, "to", -1),
                List.copyOf(phases),
                GsonHelper.getAsString(json, "moon", ""),
                GsonHelper.getAsInt(y, "min", Integer.MIN_VALUE),
                GsonHelper.getAsInt(y, "max", Integer.MAX_VALUE),
                strings(json, "seasons"),
                GsonHelper.getAsLong(age, "min", Long.MIN_VALUE),
                GsonHelper.getAsLong(age, "max", Long.MAX_VALUE),
                state(GsonHelper.getAsString(json, "after", "")),
                state(GsonHelper.getAsString(coming, "weather", "")),
                GsonHelper.getAsInt(coming, "within", 0),
                Release.parse(GsonHelper.getAsJsonObject(json, "release", new JsonObject())));
    }

    private static Forecast.State state(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "clear" -> Forecast.State.CLEAR;
            case "rain" -> Forecast.State.RAIN;
            case "thunder" -> Forecast.State.THUNDER;
            default -> null;
        };
    }

    /** The biome list, with every id checked now: a bad id fails this entry at load, not a jar in a player's hand. */
    private static List<String> biomes(JsonObject json) {
        List<String> list = strings(json, "biomes");
        for (String spec : list) {
            Identifier.parse(spec.startsWith("#") ? spec.substring(1) : spec);
        }
        return list;
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, key, new JsonArray())) {
            list.add(e.getAsString());
        }
        return List.copyOf(list);
    }

    /** Whether this sky is the one over {@code pos} right now. Server side only: climate, moons and seasons live there. */
    public boolean matches(ServerLevel level, BlockPos pos) {
        if (needsSky && !level.canSeeSky(pos)) {
            return false;
        }
        if (!dimension.isEmpty() && !dimension.equals(level.dimension().identifier().toString())) {
            return false;
        }
        if (pos.getY() < minY || pos.getY() > maxY) {
            return false;
        }
        Holder<Biome> biome = level.getBiome(pos);
        if (!biomes.isEmpty() && biomes.stream().noneMatch(b -> biomeIs(biome, b))) {
            return false;
        }
        long day = level.getDefaultClockTime();
        int hour = (int) Math.floorMod(day, 24000L);
        if (timeFrom >= 0 && timeTo >= 0) {
            boolean in = timeFrom <= timeTo ? hour >= timeFrom && hour <= timeTo : hour >= timeFrom || hour <= timeTo;
            if (!in) {
                return false;
            }
        }
        if (!moonPhases.isEmpty() && !moonPhases.contains(Moons.moonPhase(day))) {
            return false;
        }
        if (!moon.isEmpty() && !Moons.isUp(level, moon)) {
            return false;
        }
        if (!seasons.isEmpty()) {
            Season season = Seasons.current(level);
            if (season == null || !seasons.contains(season.id().toString())) {
                return false;
            }
        }
        if (!weatherMatches(level, pos, biome.value())) {
            return false;
        }
        if (minAge != Long.MIN_VALUE || maxAge != Long.MAX_VALUE) {
            long a = WeatherClock.age(level);
            if (a < minAge || a > maxAge) {
                return false;
            }
        }
        if (after != null && WeatherClock.before(level) != after) {
            return false;
        }
        if (coming != null) {
            int t = Forecast.ticksTo(level, coming);
            if (t <= 0 || t > comingWithin) {
                return false;
            }
        }
        double temperature = Climate.temperature(level, pos);
        if (temperature < minTemperature || temperature > maxTemperature) {
            return false;
        }
        double humidity = Climate.humidity(level, pos);
        return humidity >= minHumidity && humidity <= maxHumidity;
    }

    private boolean weatherMatches(ServerLevel level, BlockPos pos, Biome biome) {
        if (weather == Weather.ANY) {
            return true;
        }
        Biome.Precipitation p = level.isRaining() ? biome.getPrecipitationAt(pos, level.getSeaLevel()) : Biome.Precipitation.NONE;
        return switch (weather) {
            case CLEAR -> p == Biome.Precipitation.NONE;
            case RAIN -> p == Biome.Precipitation.RAIN;
            case SNOW -> p == Biome.Precipitation.SNOW;
            case THUNDER -> level.isThundering() && p != Biome.Precipitation.NONE;
            case THUNDERSNOW -> level.isThundering() && p == Biome.Precipitation.SNOW;
            case DRY_THUNDER -> level.isThundering()
                    && biome.getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.NONE;
            default -> true;
        };
    }

    private static boolean biomeIs(Holder<Biome> biome, String spec) {
        if (spec.startsWith("#")) {
            return biome.is(TagKey.create(Registries.BIOME, Identifier.parse(spec.substring(1))));
        }
        return biome.is(ResourceKey.create(Registries.BIOME, Identifier.parse(spec)));
    }
}
