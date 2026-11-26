package dev.tempestas;

import dev.tempestas.block.CondenserBlock;
import dev.tempestas.block.CondenserBlockEntity;
import dev.tempestas.block.StormGlassBlock;
import dev.tempestas.item.AlmanacItem;
import dev.tempestas.item.ClimateGaugeItem;
import dev.tempestas.item.CaughtSkyItem;
import dev.tempestas.item.SkyJarItem;
import dev.tempestas.sky.Forecast;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class TempestasRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Tempestas.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Tempestas.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Tempestas.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tempestas.MOD_ID);

    public static final DeferredBlock<CondenserBlock> CONDENSER = BLOCKS.registerBlock("weather_condenser",
            CondenserBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<Block> EMBER_SALT_BLOCK = BLOCKS.registerSimpleBlock("ember_salt_block",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5F)
                    .sound(SoundType.CALCITE).requiresCorrectToolForDrops().lightLevel(s -> 6));
    public static final DeferredBlock<Block> RIME_SALT_BLOCK = BLOCKS.registerSimpleBlock("rime_salt_block",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(1.5F)
                    .sound(SoundType.CALCITE).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> DEWSTONE = BLOCKS.registerSimpleBlock("dewstone",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(1.5F)
                    .sound(SoundType.CALCITE).requiresCorrectToolForDrops());

    public static final DeferredBlock<StormGlassBlock> STORM_GLASS_BLOCK = BLOCKS.registerBlock("storm_glass",
            StormGlassBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.3F)
                    .sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.DESTROY)
                    .lightLevel(s -> s.getValue(StormGlassBlock.READING) == Forecast.Reading.STORM ? 4 : 0));

    public static final DeferredItem<?> CONDENSER_ITEM = ITEMS.registerSimpleBlockItem(CONDENSER);
    public static final DeferredItem<?> EMBER_SALT_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(EMBER_SALT_BLOCK);
    public static final DeferredItem<?> RIME_SALT_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(RIME_SALT_BLOCK);
    public static final DeferredItem<?> DEWSTONE_ITEM = ITEMS.registerSimpleBlockItem(DEWSTONE);

    public static final DeferredItem<Item> EMBER_SALT = ITEMS.registerSimpleItem("ember_salt");
    public static final DeferredItem<Item> RIME_SALT = ITEMS.registerSimpleItem("rime_salt");
    public static final DeferredItem<Item> DEW_PEARL = ITEMS.registerSimpleItem("dew_pearl");
    public static final DeferredItem<BlockItem> STORM_GLASS = ITEMS.registerSimpleBlockItem(STORM_GLASS_BLOCK,
            p -> p.rarity(Rarity.UNCOMMON));

    public static final DeferredItem<ClimateGaugeItem> CLIMATE_GAUGE = ITEMS.registerItem("climate_gauge", ClimateGaugeItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<AlmanacItem> ALMANAC = ITEMS.registerItem("weather_almanac", AlmanacItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<SkyJarItem> SKY_JAR = ITEMS.registerItem("sky_jar", SkyJarItem::new);
    public static final DeferredItem<CaughtSkyItem> CAUGHT_SKY = ITEMS.registerItem("caught_sky", CaughtSkyItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> EMBER_CHARM = charm("ember_charm", Rarity.COMMON);
    public static final DeferredItem<Item> RIME_CHARM = charm("rime_charm", Rarity.COMMON);
    public static final DeferredItem<Item> DEW_CHARM = charm("dew_charm", Rarity.COMMON);
    public static final DeferredItem<Item> STORM_CHARM = charm("storm_charm", Rarity.UNCOMMON);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CondenserBlockEntity>> CONDENSER_BE =
            BLOCK_ENTITIES.register("weather_condenser",
                    () -> new BlockEntityType<>(CondenserBlockEntity::new, CONDENSER.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("tempestas",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tempestas"))
                    .icon(() -> new ItemStack(CONDENSER.get()))
                    .displayItems((params, output) -> {
                        for (DeferredItem<?> item : List.of(SKY_JAR, ALMANAC, CONDENSER_ITEM, CLIMATE_GAUGE, EMBER_SALT, RIME_SALT,
                                DEW_PEARL, STORM_GLASS, EMBER_SALT_BLOCK_ITEM, RIME_SALT_BLOCK_ITEM, DEWSTONE_ITEM,
                                EMBER_CHARM, RIME_CHARM, DEW_CHARM, STORM_CHARM)) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private static DeferredItem<Item> charm(String name, Rarity rarity) {
        return ITEMS.registerSimpleItem(name, p -> p.stacksTo(1).rarity(rarity));
    }

    private TempestasRegistry() {
    }
}
