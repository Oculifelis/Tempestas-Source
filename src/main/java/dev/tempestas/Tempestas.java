package dev.tempestas;

import dev.tempestas.charm.Charms;
import dev.tempestas.climate.Condensates;
import dev.tempestas.compat.TempestasViews;
import dev.tempestas.net.CloudsPayload;
import dev.tempestas.sky.Releases;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.SkyData;
import dev.tempestas.sky.Forecast;
import dev.tempestas.sky.WeatherClock;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

@Mod(Tempestas.MOD_ID)
public class Tempestas {
    public static final String MOD_ID = "tempestas";

    public Tempestas(IEventBus modBus) {
        TempestasRegistry.BLOCKS.register(modBus);
        TempestasRegistry.ITEMS.register(modBus);
        TempestasRegistry.BLOCK_ENTITIES.register(modBus);
        TempestasRegistry.TABS.register(modBus);
        SkyData.COMPONENTS.register(modBus);
        SkyData.ATTACHMENTS.register(modBus);
        Condensates.init();
        Charms.init();
        Forecast.init();
        WeatherClock.init();
        Skies.init();
        Releases.init();
        CloudsPayload.init(modBus);
        TempestasViews.init();
        modBus.addListener(Tempestas::registerCapabilities);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // One output slot: hoppers and pipes may take from it, never put into it.
        event.registerBlockEntity(Capabilities.Item.BLOCK, TempestasRegistry.CONDENSER_BE.get(),
                (be, side) -> side == null ? VanillaContainerWrapper.of(be) : new WorldlyContainerWrapper(be, side));
    }
}
