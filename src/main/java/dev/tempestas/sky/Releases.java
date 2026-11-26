package dev.tempestas.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.tempestas.net.CloudsPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skies let out of jars. Each is a short-lived patch of weather kept in memory on the server: no block, no entity.
 * A patch lasts a minute or so, so one cut short by a restart is not worth saving.
 */
public final class Releases {
    private static final class Active {
        final Identifier id;
        final Sky sky;
        final BlockPos center;
        int left;

        Active(Identifier id, Sky sky, BlockPos center) {
            this.id = id;
            this.sky = sky;
            this.center = center;
            this.left = sky.release().duration();
        }
    }

    private static final Map<ResourceKey<Level>, List<Active>> ACTIVE = new HashMap<>();
    private static final int MAX_PER_LEVEL = 32;
    private static final double SYNC_RANGE = 192.0;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Releases::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ACTIVE.clear());
    }

    public static void release(ServerLevel level, BlockPos center, Identifier id, Sky sky) {
        List<Active> list = ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        if (list.size() >= MAX_PER_LEVEL) {
            list.removeFirst();
        }
        Active a = new Active(id, sky, center);
        list.add(a);
        Sky.Release r = sky.release();
        level.playSound(null, center, r.sound() == null ? SoundEvents.BOTTLE_EMPTY
                : BuiltInRegistries.SOUND_EVENT.getOptional(r.sound()).orElse(SoundEvents.BOTTLE_EMPTY),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        for (SkyEffect effect : r.effects()) {
            effect.start(level, center, r.radius());
        }
        syncClouds(level, list, a);
    }

    private static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Active> list = ACTIVE.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            syncClouds(level, list, null);
        }
        Iterator<Active> it = list.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            if (--a.left <= 0) {
                it.remove();
                continue;
            }
            if (!level.isLoaded(a.center)) {
                continue;
            }
            Sky.Release r = a.sky.release();
            if (a.left % 4 == 0) {
                drifting(level, a.center, r);
            }
            falling(level, a.center, r);
            if (a.left % 20 == 0) {
                for (SkyEffect effect : r.effects()) {
                    effect.second(level, a.center, r.radius());
                }
            }
        }
    }

    /** Particle ids resolved once each; an id that names no simple particle maps to empty. */
    private static final Map<String, Optional<SimpleParticleType>> TYPES = new HashMap<>();

    private static SimpleParticleType type(String id) {
        return TYPES.computeIfAbsent(id, k -> {
            Identifier key = Identifier.tryParse(k);
            ParticleType<?> type = key == null ? null : BuiltInRegistries.PARTICLE_TYPE.getValue(key);
            return type instanceof SimpleParticleType simple ? Optional.of(simple) : Optional.empty();
        }).orElse(null);
    }

    /** A random point in a disc of the given radius around the patch centre: {x, z}. */
    private static double[] spot(RandomSource rand, BlockPos center, double radius) {
        double a = rand.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(rand.nextDouble()) * radius;
        return new double[]{center.getX() + 0.5 + Math.cos(a) * d, center.getZ() + 0.5 + Math.sin(a) * d};
    }

    private static void drifting(ServerLevel level, BlockPos center, Sky.Release r) {
        RandomSource rand = level.getRandom();
        for (String p : r.particles()) {
            SimpleParticleType simple = type(p);
            if (simple == null) {
                continue;
            }
            for (int i = 0; i < 3; i++) {
                double[] at = spot(rand, center, r.radius());
                level.sendParticles(simple, at[0], center.getY() + 1 + rand.nextDouble() * 4, at[1], 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    /** Sends the clouds near each player in this level; with {@code only} set, just that cloud to the players near it. */
    private static void syncClouds(ServerLevel level, List<Active> list, Active only) {
        for (ServerPlayer player : level.players()) {
            List<CloudsPayload.Entry> near = new ArrayList<>();
            for (Active a : list) {
                Sky.Cloud c = a.sky.release().cloud();
                if (c == null || (only != null && a != only)
                        || player.distanceToSqr(a.center.getX() + 0.5, a.center.getY(), a.center.getZ() + 0.5) > SYNC_RANGE * SYNC_RANGE) {
                    continue;
                }
                near.add(new CloudsPayload.Entry(a.center.getX(), a.center.getY(), a.center.getZ(), a.sky.release().radius(),
                        c.height(), c.spread(), c.thickness(), c.darkness(), a.left, a.sky.release().duration()));
            }
            if (!near.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new CloudsPayload(near));
            }
        }
    }

    /** Rain or snow: spread through the whole column, so the patch is full the moment it opens. */
    private static void falling(ServerLevel level, BlockPos center, Sky.Release r) {
        if (r.falling().isEmpty() || r.fallingRate() <= 0) {
            return;
        }
        RandomSource rand = level.getRandom();
        for (int i = 0; i < r.fallingRate(); i++) {
            SimpleParticleType simple = type(r.falling().get(rand.nextInt(r.falling().size())));
            if (simple == null) {
                continue;
            }
            double[] at = spot(rand, center, r.radius());
            double y = center.getY() + 1 + rand.nextDouble() * (r.ceiling() - 1);
            // Count 0 sends one particle moving along (dx, dy, dz) times speed: straight down with a little drift.
            level.sendParticles(simple, at[0], y, at[1], 0, (rand.nextDouble() - 0.5) * 0.3, -1.0,
                    (rand.nextDouble() - 0.5) * 0.3, 0.12);
        }
    }

    private Releases() {
    }
}
