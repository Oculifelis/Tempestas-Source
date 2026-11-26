package dev.tempestas.sky;

import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One thing a let-out sky does, from the {@code effects} list of a sky's {@code release}. Types: {@code grow},
 * {@code water}, {@code snow}, {@code freeze}, {@code melt}, {@code lightning}, {@code charge}, {@code potion}
 * and {@code calm}.
 * Block effects try {@code samples} spots a second, each with {@code chance}.
 */
public record SkyEffect(String type, float chance, int samples, int count, Identifier effect, int amplifier,
                        boolean playersOnly, int maxLayers) {

    static SkyEffect parse(JsonObject o) {
        String effect = GsonHelper.getAsString(o, "effect", "");
        return new SkyEffect(GsonHelper.getAsString(o, "type").toLowerCase(Locale.ROOT),
                GsonHelper.getAsFloat(o, "chance", 0.3F),
                Math.max(1, GsonHelper.getAsInt(o, "samples", 8)),
                Math.max(1, GsonHelper.getAsInt(o, "count", 1)),
                effect.isEmpty() ? null : Identifier.parse(effect),
                GsonHelper.getAsInt(o, "amplifier", 0),
                GsonHelper.getAsBoolean(o, "players_only", true),
                Math.max(1, Math.min(8, GsonHelper.getAsInt(o, "max_layers", 2))));
    }

    /** Runs once, the moment the jar is opened. */
    void start(ServerLevel level, BlockPos center, int radius) {
        if (type.equals("charge")) {
            charge(level, center, radius);
            return;
        }
        if (!type.equals("lightning")) {
            return;
        }
        RandomSource r = level.getRandom();
        for (int i = 0; i < count; i++) {
            BlockPos at = surface(level, center, radius, r);
            if (at == null) {
                continue;
            }
            LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.addFreshEntity(bolt);
            }
        }
    }

    private static EntityDataAccessor<Boolean> powered;

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Boolean> powered() {
        if (powered == null) {
            try {
                Field f = Creeper.class.getDeclaredField("DATA_IS_POWERED");
                f.setAccessible(true);
                powered = (EntityDataAccessor<Boolean>) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Creeper charge field not found", e);
            }
        }
        return powered;
    }

    /** Charges the nearest creepers in the radius and wakes the lightning rods there. No bolt, no fire. */
    private void charge(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(center).inflate(radius, 4, radius);
        List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class, box, c -> c.isAlive() && !c.isPowered());
        creepers.sort(Comparator.comparingDouble(c -> c.distanceToSqr(Vec3.atCenterOf(center))));
        for (int i = 0; i < Math.min(count, creepers.size()); i++) {
            Creeper c = creepers.get(i);
            c.getEntityData().set(powered(), true);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX(), c.getY() + 1, c.getZ(), 24, 0.4, 0.8, 0.4, 0.2);
        }
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LightningRodBlock rod) {
                rod.onLightningStrike(s, level, p.immutable());
            }
        }
    }

    /** Runs once a second while the sky lasts. */
    void second(ServerLevel level, BlockPos center, int radius) {
        switch (type) {
            case "potion", "calm" -> creatures(level, center, radius);
            case "lightning", "charge" -> {
            }
            default -> {
                RandomSource r = level.getRandom();
                for (int i = 0; i < samples; i++) {
                    if (r.nextFloat() < chance) {
                        BlockPos top = surface(level, center, radius, r);
                        if (top != null) {
                            block(level, top);
                        }
                    }
                }
            }
        }
    }

    private void block(ServerLevel level, BlockPos top) {
        BlockPos below = top.below();
        BlockState ground = level.getBlockState(below);
        BlockState at = level.getBlockState(top);
        switch (type) {
            case "grow" -> {
                if (at.isRandomlyTicking()) {
                    at.randomTick(level, top, level.getRandom());
                } else if (ground.isRandomlyTicking() && !ground.is(Blocks.GRASS_BLOCK)) {
                    ground.randomTick(level, below, level.getRandom());
                }
            }
            case "water" -> {
                if (ground.getBlock() instanceof FarmlandBlock) {
                    level.setBlock(below, ground.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), 2);
                }
                if (at.getBlock() instanceof BaseFireBlock) {
                    level.removeBlock(top, false);
                }
            }
            case "snow" -> {
                if (at.is(Blocks.SNOW)) {
                    int layers = at.getValue(SnowLayerBlock.LAYERS);
                    if (layers < maxLayers) {
                        level.setBlock(top, at.setValue(SnowLayerBlock.LAYERS, layers + 1), 3);
                    }
                } else if (at.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, top)) {
                    level.setBlock(top, Blocks.SNOW.defaultBlockState(), 3);
                } else {
                    freeze(level, below);
                }
            }
            case "freeze" -> freeze(level, below);
            case "melt" -> {
                if (at.is(Blocks.SNOW)) {
                    level.removeBlock(top, false);
                } else if (ground.is(Blocks.ICE)) {
                    // Like vanilla ice melting: where water evaporates (the Nether) the ice just goes.
                    if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, below)) {
                        level.removeBlock(below, false);
                    } else {
                        level.setBlockAndUpdate(below, IceBlock.meltsInto());
                    }
                } else if (ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW)) {
                    level.removeBlock(below, false);
                }
            }
            default -> {
            }
        }
    }

    private static void freeze(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (level.getBlockState(pos).is(Blocks.WATER) && fluid.is(Fluids.WATER) && fluid.isSource()) {
            level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
        }
    }

    private void creatures(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(center).inflate(radius, 4, radius);
        if (type.equals("calm")) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy)) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            return;
        }
        Holder<MobEffect> holder = effect == null ? null : BuiltInRegistries.MOB_EFFECT.get(effect).orElse(null);
        if (holder == null) {
            return;
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !playersOnly || e instanceof Player)) {
            e.addEffect(new MobEffectInstance(holder, 60, amplifier, true, true));
        }
    }

    /** A random landing spot in the disc, or null when that column's chunk is not loaded (never loads one). */
    static BlockPos surface(ServerLevel level, BlockPos center, int radius, RandomSource r) {
        int x;
        int z;
        do {
            x = r.nextInt(radius * 2 + 1) - radius;
            z = r.nextInt(radius * 2 + 1) - radius;
        } while (x * x + z * z > radius * radius);
        if (!level.isLoaded(center.offset(x, 0, z))) {
            return null;
        }
        // Scan down from just above the opener, so a sky let out in a cave or under a roof lands on that floor.
        BlockPos.MutableBlockPos pos = center.offset(x, 2, z).mutable();
        for (int i = 0; i < 12; i++, pos.move(0, -1, 0)) {
            if (!level.getBlockState(pos).blocksMotion() && level.getBlockState(pos.below()).blocksMotion()) {
                return pos.immutable();
            }
        }
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, center.offset(x, 0, z));
    }
}
