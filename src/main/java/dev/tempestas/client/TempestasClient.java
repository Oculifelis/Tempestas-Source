package dev.tempestas.client;

import com.mojang.serialization.MapCodec;
import dev.tempestas.Tempestas;
import dev.tempestas.item.AlmanacItem;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.Sky;
import dev.tempestas.sky.SkyData;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@Mod(value = Tempestas.MOD_ID, dist = Dist.CLIENT)
public class TempestasClient {
    public TempestasClient(IEventBus modBus) {
        AlmanacItem.OPEN = sneaking -> {
            if (sneaking) {
                JournalScreen.openGuide();
            } else {
                JournalScreen.open();
            }
        };
        modBus.addListener((RegisterColorHandlersEvent.ItemTintSources e) ->
                e.register(Tempestas.id("sky"), SkyTint.CODEC));
        ReleaseClouds.init(modBus);
    }

    /** Colours the inside of a caught-sky jar with that sky's colour from the table. */
    public record SkyTint() implements ItemTintSource {
        public static final MapCodec<SkyTint> CODEC = MapCodec.unit(new SkyTint());
        private static final int UNKNOWN = 0xFF8A97A8;

        @Override
        public int calculate(ItemStack stack, ClientLevel level, LivingEntity entity) {
            CaughtSky c = stack.get(SkyData.CAUGHT_SKY.get());
            Sky sky = c == null ? null : Skies.TABLE.get(c.sky());
            return sky == null ? UNKNOWN : 0xFF000000 | sky.color();
        }

        @Override
        public MapCodec<SkyTint> type() {
            return CODEC;
        }
    }
}
