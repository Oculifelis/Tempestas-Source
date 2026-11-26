package dev.tempestas.climate;

import com.fundamentum.climate.Climate;
import com.fundamentum.data.DataTable;
import com.fundamentum.data.DataTables;
import com.fundamentum.recipeview.RecipeViews;
import dev.tempestas.Tempestas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The condensate table, synced so JEI, Jade and the guide see the same entries as the server. */
public final class Condensates {
    public static final DataTable<Condensate> TABLE = DataTables
            .register(Tempestas.id("condensates"), "tempestas/condensates", Condensate::parse)
            .synced()
            .onLoad(t -> RecipeViews.refresh());

    public static void init() {
    }

    /** Entries sorted by priority (highest first), then id, so ties break the same way everywhere. */
    public static List<Map.Entry<Identifier, Condensate>> sorted() {
        return TABLE.entries().entrySet().stream()
                .sorted(Comparator.<Map.Entry<Identifier, Condensate>>comparingInt(e -> -e.getValue().priority())
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
    }

    /** What a condenser at this spot would make right now, or null. Server side only: the client lacks the climate. */
    public static Map.Entry<Identifier, Condensate> at(Level level, BlockPos pos) {
        double temperature = Climate.temperature(level, pos);
        double humidity = Climate.humidity(level, pos);
        BlockPos above = pos.above();
        boolean raining = level.isRainingAt(above);
        boolean thundering = level.isThundering();
        boolean sky = level.canSeeSky(above);
        for (Map.Entry<Identifier, Condensate> e : sorted()) {
            if (e.getValue().matches(temperature, humidity, raining, thundering, sky) && !e.getValue().stack().isEmpty()) {
                return e;
            }
        }
        return null;
    }

    private Condensates() {
    }
}
