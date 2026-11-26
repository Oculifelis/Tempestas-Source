package dev.tempestas.climate;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of {@code data/<ns>/tempestas/condensates/<name>.json}: what a Weather Condenser makes, and the
 * climate it needs. Ranges are inclusive; a missing bound is open.
 */
public record Condensate(Identifier result, int count, int time, int priority,
                         double minTemperature, double maxTemperature, double minHumidity, double maxHumidity,
                         Weather weather, boolean sky, int color) {

    public enum Weather {
        ANY, CLEAR, RAIN, THUNDER;

        static Weather parse(String s) {
            return switch (s) {
                case "clear" -> CLEAR;
                case "rain" -> RAIN;
                case "thunder" -> THUNDER;
                default -> ANY;
            };
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static Condensate parse(JsonObject json) {
        JsonObject t = GsonHelper.getAsJsonObject(json, "temperature", new JsonObject());
        JsonObject h = GsonHelper.getAsJsonObject(json, "humidity", new JsonObject());
        String colour = GsonHelper.getAsString(json, "color", "ffffff").replace("#", "");
        return new Condensate(
                Identifier.parse(GsonHelper.getAsString(json, "result")),
                Math.max(1, GsonHelper.getAsInt(json, "count", 1)),
                Math.max(20, GsonHelper.getAsInt(json, "time", 1200)),
                GsonHelper.getAsInt(json, "priority", 0),
                GsonHelper.getAsDouble(t, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(t, "max", Double.POSITIVE_INFINITY),
                GsonHelper.getAsDouble(h, "min", Double.NEGATIVE_INFINITY),
                GsonHelper.getAsDouble(h, "max", Double.POSITIVE_INFINITY),
                Weather.parse(GsonHelper.getAsString(json, "weather", "any")),
                GsonHelper.getAsBoolean(json, "sky", false),
                Integer.parseInt(colour, 16) & 0xFFFFFF);
    }

    /** The output, never bigger than one stack of that item (a pack count above it would not fit the slot or save). */
    public ItemStack stack() {
        return BuiltInRegistries.ITEM.getOptional(result).map(i -> {
            ItemStack stack = new ItemStack(i);
            stack.setCount(Math.min(count, stack.getMaxStackSize()));
            return stack;
        }).orElse(ItemStack.EMPTY);
    }

    public boolean matches(double temperature, double humidity, boolean raining, boolean thundering, boolean openSky) {
        if (temperature < minTemperature || temperature > maxTemperature) {
            return false;
        }
        if (humidity < minHumidity || humidity > maxHumidity) {
            return false;
        }
        if (sky && !openSky) {
            return false;
        }
        return switch (weather) {
            case ANY -> true;
            case CLEAR -> !raining;
            case RAIN -> raining;
            case THUNDER -> thundering && raining;
        };
    }
}
