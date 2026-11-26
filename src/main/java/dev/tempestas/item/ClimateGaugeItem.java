package dev.tempestas.item;

import com.fundamentum.climate.Climate;
import dev.tempestas.climate.Condensate;
import dev.tempestas.climate.Condensates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Map;

/**
 * Reads the climate where you stand: temperature, humidity, and what a condenser would make there. Sneak to see
 * where each number comes from.
 */
public class ClimateGaugeItem extends Item {
    public ClimateGaugeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = player.blockPosition();
        double temperature = Climate.temperature(level, pos);
        double humidity = Climate.humidity(level, pos);
        Map.Entry<Identifier, Condensate> found = Condensates.at(level, pos);
        Component makes = found == null ? Component.translatable("item.tempestas.climate_gauge.nothing")
                : found.getValue().stack().getHoverName();
        if (player.isSecondaryUseActive()) {
            Climate.Reading t = Climate.temperatureParts(level, pos);
            Climate.Reading h = Climate.humidityParts(level, pos);
            player.sendSystemMessage(Component.translatable("item.tempestas.climate_gauge.temperature_parts",
                    f(t.total()), f(t.biome()), f(t.season()), f(t.height()), f(t.blocks()), f(t.weather())));
            player.sendSystemMessage(Component.translatable("item.tempestas.climate_gauge.humidity_parts",
                    f(h.total()), f(h.biome()), f(h.season()), f(h.height()), f(h.blocks()), f(h.weather())));
            player.sendSystemMessage(Component.translatable("item.tempestas.climate_gauge.makes", makes));
        } else {
            player.sendOverlayMessage(Component.translatable("item.tempestas.climate_gauge.reading",
                    f(temperature), f(humidity), makes));
        }
        return InteractionResult.SUCCESS;
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
