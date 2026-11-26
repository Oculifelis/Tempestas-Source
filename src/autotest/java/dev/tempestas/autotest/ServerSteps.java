package dev.tempestas.autotest;

import dev.tempestas.TempestasRegistry;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.Journal;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.SkyData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Set;

/** The checks that need the server: run on the integrated server's thread. */
final class ServerSteps {
    static final BlockPos STAND = new BlockPos(0, -60, 0);
    static final BlockPos SNOW_AT = new BlockPos(40, -60, 0);
    static double iceBefore;

    static void scene(ServerPlayer p) {
        ServerLevel level = p.level();
        Autotest.check("skies.table", Skies.TABLE.entries().size() >= 19, Skies.TABLE.entries().size() + " skies loaded");
        cmd(level, "time set noon");
        cmd(level, "weather rain 12000");
        p.teleportTo(level, STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5, Set.of(), 0.0F, 25.0F, true);
        level.setBlockAndUpdate(STAND.offset(0, 0, 3), TempestasRegistry.CONDENSER.get().defaultBlockState());
    }

    static void catchSky(ServerPlayer p) {
        ServerLevel level = p.level();
        // Fresh rain is First Drops; age the clock so this checks plain rain.
        dev.tempestas.sky.WeatherClock.markChange(level, dev.tempestas.sky.Forecast.State.CLEAR, 401);
        Autotest.info("catch.where", "raining=" + level.isRaining() + " sky over player="
                + (Skies.at(level, p.blockPosition().above()) == null ? "none" : Skies.at(level, p.blockPosition().above()).getKey()));
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TempestasRegistry.SKY_JAR.get()));
        p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
        ItemStack caught = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(TempestasRegistry.CAUGHT_SKY.get())) {
                caught = p.getInventory().getItem(i);
            }
        }
        CaughtSky c = caught.get(SkyData.CAUGHT_SKY.get());
        Autotest.check("catch.jar", c != null, c == null ? "no caught jar in inventory" : "caught " + c.sky() + " / " + c.biome() + " / day " + c.day() + " " + c.band());
        Journal j = p.getData(SkyData.JOURNAL);
        Autotest.check("catch.journal", c != null && j.has(c.sky()), "journal entries: " + j.firsts().keySet());
        if (c != null) {
            Autotest.check("catch.rain", c.sky().getPath().contains("rain"), "rain over plains gives " + c.sky());
            p.setItemInHand(InteractionHand.MAIN_HAND, caught.copy());
        }
    }

    static void releaseSnow(ServerPlayer p) {
        ServerLevel level = p.level();
        cmd(level, "weather clear 12000");
        p.teleportTo(level, SNOW_AT.getX() + 0.5, SNOW_AT.getY(), SNOW_AT.getZ() + 0.5, Set.of(), 0.0F, 60.0F, true);
        ItemStack held = p.getMainHandItem().copy();
        ItemStack snow = new ItemStack(TempestasRegistry.CAUGHT_SKY.get());
        snow.set(SkyData.CAUGHT_SKY.get(), new CaughtSky(Identifier.parse("tempestas:snowfall"), "minecraft:snowy_plains", "", 1, "noon"));
        p.setItemInHand(InteractionHand.MAIN_HAND, snow);
        p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        Autotest.info("release.snow", "snowfall let out at " + p.blockPosition());
    }

    static void countSnow(ServerPlayer p) {
        ServerLevel level = p.level();
        int snow = 0;
        for (BlockPos b : BlockPos.betweenClosed(SNOW_AT.offset(-8, -2, -8), SNOW_AT.offset(8, 2, 8))) {
            if (level.getBlockState(b).is(Blocks.SNOW)) {
                snow++;
            }
        }
        Autotest.check("release.snow", snow > 0, snow + " snow layers within 8 blocks after release");
    }

    static void equipCharm(ServerPlayer p) {
        for (var item : new net.minecraft.world.item.Item[]{TempestasRegistry.EMBER_CHARM.get(), TempestasRegistry.RIME_CHARM.get(),
                TempestasRegistry.DEW_CHARM.get(), TempestasRegistry.STORM_CHARM.get()}) {
            var slots = CuriosApi.getItemStackSlots(new ItemStack(item), p).keySet();
            Autotest.check("curios.slot." + BuiltInRegistries.ITEM.getKey(item).getPath(), slots.contains("charm"), "slots " + slots);
        }
        cmd(p.level(), "weather clear");
        iceBefore = water(p);
        ICuriosItemHandler inv = CuriosApi.getCuriosInventory(p).orElse(null);
        Autotest.check("curios.inventory", inv != null && inv.getStacksHandler("charm").isPresent(), "charm slot on the player");
        if (inv != null) {
            inv.setEquippedCurio("charm", 0, new ItemStack(TempestasRegistry.DEW_CHARM.get()));
        }
    }

    /** The Dew Charm sleeps under a clear sky, then wakes once it rains. */
    static void checkCharm(ServerPlayer p) {
        double clear = water(p);
        Autotest.check("charm.asleep", clear == iceBefore, "dew charm, clear sky: water_resistance " + iceBefore + " -> " + clear);
        cmd(p.level(), "weather rain");
    }

    static void checkCharmAwake(ServerPlayer p) {
        double rain = water(p);
        boolean regen = p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        Autotest.check("charm.awake", rain > iceBefore && regen,
                "dew charm, rain: water_resistance " + iceBefore + " -> " + rain + ", regeneration " + regen);
    }

    /** Edge cases: bad pack data and bad item data never throw, oversized outputs fit a slot, melting obeys the Nether. */
    static void edgeCases(ServerPlayer p) {
        com.google.gson.JsonObject bad = com.google.gson.JsonParser.parseString("{\"biomes\":[\"Not A Biome!\"]}").getAsJsonObject();
        boolean rejected;
        try {
            dev.tempestas.sky.Sky.parse(bad);
            rejected = false;
        } catch (RuntimeException e) {
            rejected = true;
        }
        Autotest.check("edge.sky.bad_biome", rejected, "a sky with a bad biome id fails at load");
        com.google.gson.JsonObject tag = com.google.gson.JsonParser.parseString("{\"biomes\":[\"#minecraft:is_forest\",\"minecraft:plains\"]}").getAsJsonObject();
        Autotest.check("edge.sky.good_biome", dev.tempestas.sky.Sky.parse(tag).biomes().size() == 2, "biome tags and ids still load");

        String text;
        try {
            text = Skies.provenance(new CaughtSky(Identifier.parse("tempestas:snowfall"), "Bad Biome!", "Bad Season!", 3, "noon")).getString();
        } catch (RuntimeException e) {
            text = null;
        }
        Autotest.check("edge.provenance", text != null, "bad ids in a caught jar give " + text);

        var sword = dev.tempestas.climate.Condensate.parse(com.google.gson.JsonParser.parseString(
                "{\"result\":\"minecraft:diamond_sword\",\"count\":5}").getAsJsonObject()).stack();
        var stone = dev.tempestas.climate.Condensate.parse(com.google.gson.JsonParser.parseString(
                "{\"result\":\"minecraft:stone\",\"count\":200}").getAsJsonObject()).stack();
        Autotest.check("edge.condensate.count", sword.getCount() == 1 && stone.getCount() == 64,
                "count 5 sword gives " + sword.getCount() + ", count 200 stone gives " + stone.getCount());

        ServerLevel nether = level(p, net.minecraft.world.level.Level.NETHER);
        BlockPos netherIce = new BlockPos(8, 64, 8);
        BlockPos overIce = new BlockPos(-40, -50, 0);
        var left = nether == null ? null : melt(nether, netherIce);
        Autotest.check("edge.melt.nether", left != null && left.isAir(), "ice melted by a let-out sky in the Nether leaves " + left);
        Autotest.check("edge.melt.overworld", melt(p.level(), overIce).is(Blocks.WATER), "ice melted in the overworld turns to water");
        p.level().setBlockAndUpdate(overIce, Blocks.AIR.defaultBlockState());
    }

    private static ServerLevel level(ServerPlayer p, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key) {
        return p.level().getServer().getLevel(key);
    }

    /** Puts ice at {@code ice} with air above, runs one second of a certain melt there, and gives what is left. */
    private static net.minecraft.world.level.block.state.BlockState melt(ServerLevel level, BlockPos ice) {
        level.getChunk(ice);
        level.setBlockAndUpdate(ice, Blocks.ICE.defaultBlockState());
        for (int i = 1; i <= 3; i++) {
            level.setBlockAndUpdate(ice.above(i), Blocks.AIR.defaultBlockState());
        }
        dev.tempestas.sky.SkyEffect effect = new dev.tempestas.sky.SkyEffect("melt", 1.0F, 1, 1, null, 0, true, 2);
        try {
            var second = dev.tempestas.sky.SkyEffect.class.getDeclaredMethod("second", ServerLevel.class, BlockPos.class, int.class);
            second.setAccessible(true);
            second.invoke(effect, level, ice.above(), 0);
        } catch (ReflectiveOperationException e) {
            Autotest.info("edge.melt", "could not run the effect: " + e);
        }
        return level.getBlockState(ice);
    }

    private static void cmd(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
    }

    private static double water(ServerPlayer p) {
        Holder<Attribute> a = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("fundamentum:water_resistance")).orElse(null);
        AttributeInstance inst = a == null ? null : p.getAttribute(a);
        return inst == null ? Double.NaN : inst.getValue();
    }

    private ServerSteps() {
    }
}
