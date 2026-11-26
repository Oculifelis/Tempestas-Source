package dev.tempestas.compat;

import com.fundamentum.recipeview.RecipeViews;
import com.fundamentum.recipeview.ViewCategory;
import dev.tempestas.TempestasRegistry;
import dev.tempestas.climate.Condensate;
import dev.tempestas.climate.Condensates;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recipe viewer pages, declared once through Fundamentum: one "Condensing" page per condensate entry with its climate,
 * and information pages for the salt blocks and the charms. Pages read the synced table, so datapack changes show up.
 */
public final class TempestasViews {
    public static final String CONDENSING = "tempestas:condensing";

    public static void init() {
        ViewCategory<Map.Entry<Identifier, Condensate>> c = RecipeViews.<Map.Entry<Identifier, Condensate>>category(CONDENSING)
                .title(Component.translatable("jei.tempestas.condensing"))
                .icon(() -> new ItemStack(TempestasRegistry.CONDENSER.get()))
                .size(150, 64)
                .arrow(30, 4, 0)
                .catalysts(() -> List.of(new ItemStack(TempestasRegistry.CONDENSER.get())));
        int machine = c.input(4, 4);
        int out = c.output(60, 4);
        c.recipes(Condensates::sorted)
                .key(e -> e.getKey().getNamespace() + "/" + e.getKey().getPath())
                .fill((e, page) -> {
                    Condensate k = e.getValue();
                    page.item(machine, new ItemStack(TempestasRegistry.CONDENSER.get())).item(out, k.stack());
                    page.text(Component.translatable("jei.tempestas.time", k.time() / 20), 86, 9);
                    page.text(Component.translatable("jei.tempestas.temperature",
                            range(k.minTemperature(), k.maxTemperature())), 2, 28);
                    page.text(Component.translatable("jei.tempestas.humidity",
                            range(k.minHumidity(), k.maxHumidity())), 2, 39);
                    Component weather = Component.translatable("jei.tempestas.weather." + k.weather().key());
                    page.text(k.sky() ? Component.translatable("jei.tempestas.weather_sky", weather) : weather, 2, 50);
                });

        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.EMBER_SALT_BLOCK.get())),
                () -> List.of(Component.translatable("info.tempestas.ember_salt_block")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.RIME_SALT_BLOCK.get())),
                () -> List.of(Component.translatable("info.tempestas.rime_salt_block")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.DEWSTONE.get())),
                () -> List.of(Component.translatable("info.tempestas.dewstone")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.CLIMATE_GAUGE.get())),
                () -> List.of(Component.translatable("info.tempestas.climate_gauge")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.EMBER_CHARM.get()), new ItemStack(TempestasRegistry.RIME_CHARM.get()),
                        new ItemStack(TempestasRegistry.DEW_CHARM.get()), new ItemStack(TempestasRegistry.STORM_CHARM.get())),
                () -> List.of(Component.translatable("info.tempestas.charms")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.SKY_JAR.get()), new ItemStack(TempestasRegistry.CAUGHT_SKY.get())),
                () -> List.of(Component.translatable("info.tempestas.sky_jar")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.STORM_GLASS.get())),
                () -> List.of(Component.translatable("info.tempestas.storm_glass")));
        RecipeViews.info(() -> List.of(new ItemStack(TempestasRegistry.ALMANAC.get())),
                () -> List.of(Component.translatable("info.tempestas.almanac")));
    }

    private static Component range(double min, double max) {
        boolean lo = Double.isFinite(min);
        boolean hi = Double.isFinite(max);
        if (lo && hi) {
            return Component.literal(f(min) + " - " + f(max));
        }
        if (lo) {
            return Component.literal(f(min) + "+");
        }
        if (hi) {
            return Component.translatable("jei.tempestas.at_most", f(max));
        }
        return Component.translatable("jei.tempestas.any");
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private TempestasViews() {
    }
}
