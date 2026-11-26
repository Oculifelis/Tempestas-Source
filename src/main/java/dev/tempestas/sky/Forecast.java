package dev.tempestas.sky;

import com.fundamentum.data.DataTable;
import com.fundamentum.data.DataTables;
import com.google.gson.JsonObject;
import dev.tempestas.Tempestas;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;

import java.util.Locale;

/**
 * Reads the weather timers the server already keeps, so the Storm Glass and sky conditions can tell what comes next.
 * Nothing is rolled here: every answer comes from the same countdowns vanilla uses to flip the weather.
 */
public final class Forecast {
    /** Ticks until something, or {@link #NEVER} when the timers say it will not happen. */
    public static final int NEVER = Integer.MAX_VALUE;

    public static final DataTable<Settings> TABLE = DataTables
            .register(Tempestas.id("forecast"), "tempestas/forecast", Settings::parse)
            .synced();

    private Forecast() {
    }

    public static void init() {
    }

    /** How far ahead the glass looks and what each reading sends to a comparator. */
    public record Settings(int far, int near, int[] comparator) {
        static final Settings DEFAULT = new Settings(6000, 1200, new int[] {0, 4, 9, 15, 2});

        public static Settings parse(JsonObject json) {
            JsonObject c = GsonHelper.getAsJsonObject(json, "comparator", new JsonObject());
            int[] levels = new int[Reading.values().length];
            for (Reading r : Reading.values()) {
                levels[r.ordinal()] = Math.clamp(GsonHelper.getAsInt(c, r.getSerializedName(),
                        DEFAULT.comparator[r.ordinal()]), 0, 15);
            }
            int far = Math.max(20, GsonHelper.getAsInt(json, "far", DEFAULT.far));
            int near = Math.clamp(GsonHelper.getAsInt(json, "near", DEFAULT.near), 20, far);
            return new Settings(far, near, levels);
        }
    }

    public static Settings settings() {
        Settings s = TABLE.get(Tempestas.id("storm_glass"));
        return s == null ? Settings.DEFAULT : s;
    }

    /** The five things a Storm Glass can show. */
    public enum Reading implements StringRepresentable {
        FAIR, CHANGE, RAIN, STORM, CLEARING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public int comparator() {
            return settings().comparator()[ordinal()];
        }
    }

    /** The weather in a dimension right now, in the three states the server switches between. */
    public enum State {
        CLEAR, RAIN, THUNDER;

        public static State of(ServerLevel level) {
            if (!level.isRaining()) {
                return CLEAR;
            }
            return level.isThundering() ? THUNDER : RAIN;
        }
    }

    /** False when the timers will not move: no weather here, or the weather cycle is off. */
    public static boolean active(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && level.canHaveWeather()
                && level.getGameRules().get(GameRules.ADVANCE_WEATHER);
    }

    /** Ticks until rain starts; 0 while it rains. */
    public static int ticksToRain(ServerLevel level) {
        if (level.isRaining()) {
            return 0;
        }
        if (!active(level)) {
            return NEVER;
        }
        WeatherData w = level.getWeatherData();
        int clear = w.getClearWeatherTime();
        if (clear > 0) {
            // A forced clear holds both timers at 1, so rain follows the moment it runs out.
            return clear + 1;
        }
        int rain = w.getRainTime();
        return rain <= 0 ? NEVER : rain;
    }

    /** Ticks until the rain stops while it rains, else {@link #NEVER}. */
    public static int ticksToRainEnd(ServerLevel level) {
        if (!level.isRaining() || !active(level)) {
            return NEVER;
        }
        int rain = level.getWeatherData().getRainTime();
        return rain <= 0 ? NEVER : rain;
    }

    /** Ticks until a thunderstorm (rain and the thunder flag together); 0 during one. */
    public static int ticksToThunder(ServerLevel level) {
        boolean raining = level.isRaining();
        boolean thunder = level.isThundering();
        if (raining && thunder) {
            return 0;
        }
        if (!active(level)) {
            return NEVER;
        }
        WeatherData w = level.getWeatherData();
        int clear = w.getClearWeatherTime();
        int r;
        int t;
        if (clear > 0) {
            r = clear + 1;
            t = clear + 1;
        } else {
            r = raining ? 0 : w.getRainTime();
            t = thunder ? 0 : w.getThunderTime();
            if ((!raining && r <= 0) || (!thunder && t <= 0)) {
                return NEVER;
            }
        }
        int start = Math.max(r, t);
        // Whichever is already on must still be on when the other arrives.
        if (raining && w.getRainTime() < start) {
            return NEVER;
        }
        if (thunder && clear <= 0 && w.getThunderTime() < start) {
            return NEVER;
        }
        return start;
    }

    /** What a Storm Glass at this level shows now. Same order as the design: storm, clearing, rain, change, fair. */
    public static Reading reading(ServerLevel level) {
        if (!level.canHaveWeather() || !level.dimensionType().hasSkyLight()) {
            return Reading.FAIR;
        }
        Settings s = settings();
        if (ticksToThunder(level) <= s.near()) {
            return Reading.STORM;
        }
        if (ticksToRainEnd(level) <= s.near()) {
            return Reading.CLEARING;
        }
        if (ticksToRain(level) <= s.near()) {
            return Reading.RAIN;
        }
        if (ticksToRain(level) <= s.far() || ticksToThunder(level) <= s.far()) {
            return Reading.CHANGE;
        }
        return Reading.FAIR;
    }

    /** Ticks until this kind of weather begins, for the "coming" sky condition. */
    public static int ticksTo(ServerLevel level, State state) {
        return switch (state) {
            case RAIN -> level.isRaining() ? 0 : ticksToRain(level);
            case THUNDER -> ticksToThunder(level);
            case CLEAR -> level.isRaining() ? ticksToRainEnd(level) : 0;
        };
    }
}
