package dev.tempestas.sky;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers, per dimension, when the weather last switched between clear, rain and thunder, and what it was before.
 * Not saved: after a restart the current weather counts as old, so the short-lived skies wait for the next change.
 */
public final class WeatherClock {
    private record Mark(Forecast.State state, Forecast.State before, long since) {
    }

    private static final Map<ResourceKey<Level>, Mark> MARKS = new HashMap<>();

    private WeatherClock() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> MARKS.clear());
    }

    static void tick(ServerLevel level) {
        Forecast.State now = Forecast.State.of(level);
        Mark m = MARKS.get(level.dimension());
        if (m == null) {
            MARKS.put(level.dimension(), new Mark(now, null, Long.MIN_VALUE / 2));
        } else if (m.state() != now) {
            MARKS.put(level.dimension(), new Mark(now, m.state(), level.getGameTime()));
        }
    }

    /** Ticks since the weather last changed here; huge when no change has been seen since the server started. */
    public static long age(ServerLevel level) {
        Mark m = MARKS.get(level.dimension());
        if (m == null || m.state() != Forecast.State.of(level)) {
            return 0;
        }
        return level.getGameTime() - m.since();
    }

    /** The weather before the current one, or null when no change has been seen. */
    public static Forecast.State before(ServerLevel level) {
        Mark m = MARKS.get(level.dimension());
        return m == null || m.state() != Forecast.State.of(level) ? null : m.before();
    }

    /** Test hook: pretend the weather here changed from {@code before}, {@code ago} ticks back. */
    public static void markChange(ServerLevel level, Forecast.State before, long ago) {
        MARKS.put(level.dimension(), new Mark(Forecast.State.of(level), before, level.getGameTime() - ago));
    }
}
