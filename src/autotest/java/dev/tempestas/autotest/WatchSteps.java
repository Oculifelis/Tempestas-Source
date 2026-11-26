package dev.tempestas.autotest;

import dev.tempestas.TempestasRegistry;
import dev.tempestas.block.StormGlassBlock;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.Forecast;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.Sky;
import dev.tempestas.sky.SkyData;
import dev.tempestas.sky.WeatherClock;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/** The 0.2.0 Storm Watch checks: forecast readings, the weather clock, the five new skies and the charge effect. */
final class WatchSteps {
    static final BlockPos STAND = ServerSteps.STAND;
    static final BlockPos GLASS = STAND.offset(2, 0, 0);
    static final BlockPos DESERT = STAND.offset(-30, 0, 0);
    static final BlockPos SNOWY = STAND.offset(0, 0, -30);

    /** Sets the weather flags, timers and visible levels at once, so the change shows without waiting. */
    private static void weather(ServerLevel level, boolean rain, boolean thunder, int clear, int rainTime, int thunderTime) {
        WeatherData w = level.getWeatherData();
        w.setRaining(rain);
        w.setThundering(thunder);
        w.setClearWeatherTime(clear);
        w.setRainTime(rainTime);
        w.setThunderTime(thunderTime);
        level.setRainLevel(rain ? 1.0F : 0.0F);
        level.oRainLevel = level.rainLevel;
        level.setThunderLevel(thunder ? 1.0F : 0.0F);
        level.oThunderLevel = level.thunderLevel;
    }

    private static void reading(ServerLevel level, String name, Forecast.Reading want, int comparator) {
        Forecast.Reading got = Forecast.reading(level);
        BlockState state = StormGlassBlock.refresh(level.getBlockState(GLASS), level, GLASS);
        int signal = state.getAnalogOutputSignal(level, GLASS, Direction.NORTH);
        Autotest.check("forecast." + name, got == want && state.getValue(StormGlassBlock.READING) == want
                && signal == comparator, "reading " + got + ", glass " + state.getValue(StormGlassBlock.READING)
                + ", comparator " + signal + " (want " + want + "/" + comparator + ")");
    }

    private static Sky sky(String path) {
        return Skies.TABLE.get(Identifier.fromNamespaceAndPath("tempestas", path));
    }

    private static String at(ServerLevel level, BlockPos pos) {
        Map.Entry<Identifier, Sky> e = Skies.at(level, pos);
        return e == null ? "none" : e.getKey().toString();
    }

    static void forecast(ServerPlayer p) {
        ServerLevel level = p.level();
        Autotest.check("skies.new", sky("first_drops") != null && sky("rainbow") != null && sky("calm_before") != null
                && sky("dry_thunder") != null && sky("thundersnow") != null, Skies.TABLE.entries().size() + " skies loaded");
        cmd(level, "fillbiome " + (DESERT.getX() - 6) + " -64 -6 " + (DESERT.getX() + 6) + " -40 6 minecraft:desert");
        cmd(level, "fillbiome -6 -64 " + (SNOWY.getZ() - 6) + " 6 -40 " + (SNOWY.getZ() + 6) + " minecraft:snowy_plains");
        level.setBlockAndUpdate(GLASS, TempestasRegistry.STORM_GLASS_BLOCK.get().defaultBlockState());
        Autotest.check("glass.placed", level.getBlockState(GLASS).is(TempestasRegistry.STORM_GLASS_BLOCK.get()),
                "storm glass at " + GLASS + ": " + level.getBlockState(GLASS));

        weather(level, false, false, 0, 20000, 30000);
        reading(level, "fair", Forecast.Reading.FAIR, 0);
        weather(level, false, false, 0, 3000, 50000);
        reading(level, "change", Forecast.Reading.CHANGE, 4);
        weather(level, false, false, 0, 600, 50000);
        reading(level, "rain", Forecast.Reading.RAIN, 9);
        weather(level, false, false, 0, 600, 800);
        reading(level, "storm", Forecast.Reading.STORM, 15);
        weather(level, true, false, 0, 500, 50000);
        reading(level, "clearing", Forecast.Reading.CLEARING, 2);
        // Rain that stops before the thunder timer runs out never makes a storm.
        weather(level, true, false, 0, 300, 900);
        Forecast.Reading edge = Forecast.reading(level);
        Autotest.check("forecast.edge", edge != Forecast.Reading.STORM, "short rain, later thunder reads " + edge);
        weather(level, false, false, 0, 20000, 30000);
        cmd(level, "weather clear 12000");
        level.setRainLevel(0.0F);
        level.setThunderLevel(0.0F);
    }

    static void rainStart(ServerPlayer p) {
        Autotest.info("clock.clear", "state " + Forecast.State.of(p.level()) + ", age " + WeatherClock.age(p.level()));
        cmd(p.level(), "weather rain 12000");
    }

    static void rainCheck(ServerPlayer p) {
        ServerLevel level = p.level();
        long age = WeatherClock.age(level);
        Forecast.State before = WeatherClock.before(level);
        Autotest.check("clock.rain", Forecast.State.of(level) == Forecast.State.RAIN && age > 0 && age <= 60
                && before == Forecast.State.CLEAR, "state " + Forecast.State.of(level) + ", age " + age + ", after " + before);
        Autotest.check("sky.first_drops", sky("first_drops").matches(level, STAND), "fresh rain at " + STAND
                + ", sky here " + at(level, STAND));
        WeatherClock.markChange(level, Forecast.State.CLEAR, 401);
        Autotest.check("sky.first_drops.window", !sky("first_drops").matches(level, STAND), "rain 401 ticks old, sky here "
                + at(level, STAND));
        cmd(level, "weather clear 12000");
    }

    static void clearCheck(ServerPlayer p) {
        ServerLevel level = p.level();
        long age = WeatherClock.age(level);
        Forecast.State before = WeatherClock.before(level);
        Autotest.check("clock.clear", Forecast.State.of(level) == Forecast.State.CLEAR && age > 0 && age <= 140
                && before == Forecast.State.RAIN, "state " + Forecast.State.of(level) + ", age " + age + ", after " + before);
        Autotest.check("sky.rainbow", sky("rainbow").matches(level, STAND), "rain just stopped at noon, sky here "
                + at(level, STAND));
        WeatherClock.markChange(level, Forecast.State.RAIN, 601);
        Autotest.check("sky.rainbow.window", !sky("rainbow").matches(level, STAND), "clear 601 ticks, sky here "
                + at(level, STAND));

        weather(level, false, false, 0, 2500, 2401);
        Autotest.check("sky.calm_before.window", !sky("calm_before").matches(level, STAND), "thunder in 2501, sky here "
                + at(level, STAND));
        weather(level, false, false, 0, 1000, 1500);
        Autotest.check("sky.calm_before", sky("calm_before").matches(level, STAND), "thunder in 1500, sky here "
                + at(level, STAND));
        catchHere(p, "calm_before");
        AdvancementHolder adv = level.getServer().getAdvancements().get(Identifier.parse("tempestas:caught/calm_before"));
        Autotest.check("advancement.ahead_of_the_storm", adv != null && p.getAdvancements().getOrStartProgress(adv).isDone(),
                adv == null ? "advancement missing" : "granted on catch");

        weather(level, true, true, 0, 6000, 6000);
        String desert = at(level, DESERT);
        String plains = at(level, STAND);
        Autotest.check("sky.dry_thunder", desert.equals("tempestas:dry_thunder"), "desert in thunder gives " + desert);
        Autotest.check("sky.dry_thunder.plains", !plains.equals("tempestas:dry_thunder")
                && sky("thunderhead").matches(level, STAND), "plains in thunder gives " + plains);
        String snowy = at(level, SNOWY);
        Autotest.check("sky.thundersnow", snowy.equals("tempestas:thundersnow"), "snowy plains in thunder gives " + snowy);
        weather(level, false, false, 0, 20000, 30000);
        cmd(level, "weather clear 12000");
    }

    private static void catchHere(ServerPlayer p, String want) {
        ServerLevel level = p.level();
        ItemStack held = p.getMainHandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TempestasRegistry.SKY_JAR.get()));
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        String sky = at(level, eye);
        p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
        Autotest.check("catch." + want, sky.equals("tempestas:" + want), "jar opened under " + sky);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
    }

    static void chargeSetup(ServerPlayer p) {
        ServerLevel level = p.level();
        cmd(level, "difficulty normal");
        // The player stands wherever the snow test left them, so place everything around the player.
        BlockPos here = p.blockPosition();
        spawnCreeper(level, here.offset(3, 0, -2), "tp_near");
        spawnCreeper(level, here.offset(20, 0, -2), "tp_far");
        ItemStack held = p.getMainHandItem().copy();
        ItemStack jar = new ItemStack(TempestasRegistry.CAUGHT_SKY.get());
        jar.set(SkyData.CAUGHT_SKY.get(), new CaughtSky(Identifier.parse("tempestas:dry_thunder"), "minecraft:desert", "", 1, "noon"));
        p.setItemInHand(InteractionHand.MAIN_HAND, jar);
        p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        Autotest.info("charge.release", "dry thunder let out at " + p.blockPosition());
    }

    private static void spawnCreeper(ServerLevel level, BlockPos pos, String tag) {
        Creeper c = net.minecraft.world.entity.EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
        if (c == null) {
            return;
        }
        c.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        c.setNoAi(true);
        c.setPersistenceRequired();
        c.addTag(tag);
        level.addFreshEntity(c);
    }

    static void chargeCheck(ServerPlayer p) {
        ServerLevel level = p.level();
        BlockPos here = p.blockPosition();
        List<Creeper> all = level.getEntitiesOfClass(Creeper.class, new AABB(here).inflate(40));
        Creeper near = all.stream().filter(c -> c.entityTags().contains("tp_near")).findFirst().orElse(null);
        Creeper far = all.stream().filter(c -> c.entityTags().contains("tp_far")).findFirst().orElse(null);
        Autotest.check("charge.near", near != null && near.isPowered(), near == null ? "near creeper missing" : "near powered " + near.isPowered());
        Autotest.check("charge.far", far != null && !far.isPowered(), far == null ? "far creeper missing" : "far powered " + far.isPowered());
        int fire = 0;
        for (BlockPos b : BlockPos.betweenClosed(here.offset(-10, -2, -10), here.offset(10, 4, 10))) {
            if (level.getBlockState(b).is(Blocks.FIRE)) {
                fire++;
            }
        }
        Autotest.check("charge.no_fire", fire == 0, fire + " fire blocks near the release");
        all.forEach(c -> c.discard());
        cmd(level, "difficulty peaceful");
        // Swap the condenser in front of the camera for a Storm Glass so the last screenshot shows its Jade line.
        level.setBlockAndUpdate(here.offset(0, 0, 3), TempestasRegistry.STORM_GLASS_BLOCK.get().defaultBlockState());
    }

    private static void cmd(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
    }

    private WatchSteps() {
    }
}
