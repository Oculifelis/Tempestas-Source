package dev.tempestas.autotest;

import dev.tempestas.TempestasRegistry;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.SkyData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Lets out rain, a thunderhead and snow on open ground and stands the camera off to the side for a picture of each. */
final class LookSteps {
    static final BlockPos RAIN = ServerSteps.STAND.offset(-40, 0, 80);
    static final BlockPos STORM = ServerSteps.STAND.offset(0, 0, 80);
    static final BlockPos SNOW = ServerSteps.STAND.offset(40, 0, 80);

    static void rain(ServerPlayer p) {
        letOut(p, "rain_shower", "minecraft:plains", RAIN);
    }

    static void storm(ServerPlayer p) {
        letOut(p, "thunderhead", "minecraft:plains", STORM);
    }

    static void snow(ServerPlayer p) {
        letOut(p, "snowfall", "minecraft:snowy_plains", SNOW);
    }

    private static void letOut(ServerPlayer p, String sky, String biome, BlockPos at) {
        ServerLevel level = p.level();
        cmd(level, "weather clear 12000");
        cmd(level, "time set 6000");
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
        ItemStack held = p.getMainHandItem().copy();
        ItemStack jar = new ItemStack(TempestasRegistry.CAUGHT_SKY.get());
        jar.set(SkyData.CAUGHT_SKY.get(), new CaughtSky(Identifier.parse("tempestas:" + sky), biome, "", 1, "noon"));
        p.setItemInHand(InteractionHand.MAIN_HAND, jar);
        p.getMainHandItem().use(level, p, InteractionHand.MAIN_HAND);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        // Step back to the south and look north, a little upward, so the ground and the sky over the patch both show.
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 14.5, Set.of(), 180.0F, -14.0F, true);
        Autotest.info("look." + sky, sky + " let out at " + at);
    }

    private static void cmd(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
    }

    private LookSteps() {
    }
}
