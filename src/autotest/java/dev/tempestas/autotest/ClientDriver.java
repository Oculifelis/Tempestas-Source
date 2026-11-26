package dev.tempestas.autotest;

import dev.tempestas.client.JournalScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Makes a fresh superflat world, then: catches rain in a Sky Jar, photographs the tinted jar, opens the journal and the
 * guide, checks JEI and opens the condensing page, looks at the condenser for Jade, lets out a jar of snow and counts
 * the snow, equips a charm through Curios and checks its attribute. Stops the client at the end.
 */
@EventBusSubscriber(modid = "tempestas_autotest", value = Dist.CLIENT)
public final class ClientDriver {
    private static final String WORLD = "tptest";
    private static final long TIMEOUT_NANOS = 6L * 60L * 1_000_000_000L;
    private static boolean creating;
    private static long startedAt;
    private static int step;
    private static int wait;
    private static int idle;
    private static boolean finished;

    private ClientDriver() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!Autotest.ON || finished) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (startedAt != 0 && System.nanoTime() - startedAt > TIMEOUT_NANOS) {
            Autotest.check("timeout", false, "ran out of time at step " + step);
            stop(mc);
            return;
        }
        try {
            drive(mc);
        } catch (Throwable t) {
            Autotest.fails++;
            Autotest.LOG.error("TPTEST FAIL exception :: step " + step + " threw", t);
            stop(mc);
        }
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> job) {
        IntegratedServer s = mc.getSingleplayerServer();
        s.execute(() -> {
            try {
                job.accept(s.getPlayerList().getPlayers().getFirst());
            } catch (Throwable t) {
                Autotest.fails++;
                Autotest.LOG.error("TPTEST FAIL exception :: server step " + step + " threw", t);
            }
        });
    }

    private static void drive(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.gui.screen() instanceof TitleScreen title && mc.gui.overlay() == null) {
                creating = true;
                startedAt = System.nanoTime();
                Autotest.info("client", "title screen, window visible="
                        + GLFW.glfwGetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_VISIBLE)
                        + " focused=" + GLFW.glfwGetWindowAttrib(mc.getWindow().handle(), GLFW.GLFW_FOCUSED));
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.renderDistance().set(5);
                mc.options.simulationDistance().set(5);
                LevelStorageSource source = mc.getLevelSource();
                if (source.levelExists(WORLD)) {
                    try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
                        access.deleteLevel();
                    } catch (Exception e) {
                        Autotest.LOG.warn("TPTEST could not delete old world", e);
                    }
                }
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261115L, false, false),
                        ra -> ra.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), title);
            } else if (!creating && ++idle > 200 && mc.gui.overlay() == null && !(mc.gui.screen() instanceof TitleScreen)) {
                // 26.2 can open on another first screen; go to the title screen so the run starts
                idle = 0;
                mc.gui.setScreen(new TitleScreen());
            }
            return;
        }
        if (mc.gui.screen() instanceof PauseScreen) {
            mc.gui.setScreen(null);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case 0 -> { server(mc, ServerSteps::scene); step = 1; wait = 100; }
            case 1 -> { server(mc, ServerSteps::catchSky); step = 2; wait = 40; }
            case 2 -> { shot(mc, "1_jar_of_rain"); step = 3; wait = 5; }
            case 3 -> { JournalScreen.open(); step = 4; wait = 20; }
            case 4 -> {
                Autotest.check("journal.screen", mc.gui.screen() instanceof JournalScreen, "screen " + name(mc));
                shot(mc, "2_journal");
                step = 5;
                wait = 5;
            }
            case 5 -> { JournalScreen.openGuide(); step = 6; wait = 20; }
            case 6 -> {
                Autotest.check("guide.screen", mc.gui.screen() != null && !(mc.gui.screen() instanceof JournalScreen), "screen " + name(mc));
                shot(mc, "3_guide");
                mc.gui.setScreen(null);
                step = 7;
                wait = 10;
            }
            case 7 -> {
                if (AutotestJeiPlugin.runtime == null) {
                    Autotest.check("jei.runtime", false, "JEI runtime never became available");
                    step = 9;
                } else {
                    AutotestJeiPlugin.check();
                    step = 8;
                    wait = 40;
                }
            }
            case 8 -> { Autotest.info("jei.screen", name(mc)); shot(mc, "4_jei_condensing"); mc.gui.setScreen(null); step = 9; wait = 40; }
            case 9 -> { shot(mc, "5_condenser_jade"); step = 10; wait = 5; }
            case 10 -> { server(mc, ServerSteps::releaseSnow); step = 11; wait = 300; }
            case 11 -> { server(mc, ServerSteps::countSnow); server(mc, ServerSteps::edgeCases); shot(mc, "6_snow_let_out"); step = 12; wait = 10; }
            case 12 -> { server(mc, ServerSteps::equipCharm); step = 13; wait = 40; }
            case 13 -> { server(mc, ServerSteps::checkCharm); step = 14; wait = 40; }
            case 14 -> { server(mc, ServerSteps::checkCharmAwake); step = 15; wait = 20; }
            case 15 -> { server(mc, WatchSteps::forecast); step = 16; wait = 20; }
            case 16 -> { server(mc, WatchSteps::rainStart); step = 17; wait = 60; }
            case 17 -> { server(mc, WatchSteps::rainCheck); step = 18; wait = 140; }
            case 18 -> { server(mc, WatchSteps::clearCheck); step = 19; wait = 10; }
            case 19 -> { server(mc, WatchSteps::chargeSetup); step = 20; wait = 30; }
            case 20 -> { server(mc, WatchSteps::chargeCheck); step = 21; wait = 30; }
            case 21 -> { shot(mc, "7_storm_glass_jade"); step = 22; wait = 5; }
            case 22 -> { server(mc, LookSteps::rain); if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); step = 23; wait = 80; }
            case 23 -> { shot(mc, "8_rain_let_out"); step = 24; wait = 5; }
            case 24 -> { server(mc, LookSteps::storm); step = 25; wait = 80; }
            case 25 -> { shot(mc, "9_storm_let_out"); step = 26; wait = 5; }
            case 26 -> { server(mc, LookSteps::snow); step = 27; wait = 100; }
            case 27 -> { shot(mc, "10_snow_side"); if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); step = 28; wait = 5; }
            default -> stop(mc);
        }
    }

    private static String name(Minecraft mc) {
        return mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName();
    }

    private static void shot(Minecraft mc, String name) {
        String file = "tptest_" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        Autotest.info("shot", file);
    }

    private static void stop(Minecraft mc) {
        finished = true;
        Autotest.LOG.info("TPTEST done, fails={}", Autotest.fails);
        mc.stop();
    }
}
