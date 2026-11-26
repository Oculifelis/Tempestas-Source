package dev.tempestas.block;

import com.mojang.serialization.MapCodec;
import dev.tempestas.sky.Forecast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A sealed glass of crystals that shows what the weather timers say is coming. It checks every five seconds, chimes
 * softly when the reading changes, and gives a comparator a level per reading.
 */
public class StormGlassBlock extends Block {
    public static final MapCodec<StormGlassBlock> CODEC = simpleCodec(StormGlassBlock::new);
    public static final EnumProperty<Forecast.Reading> READING = EnumProperty.create("reading", Forecast.Reading.class);
    public static final int INTERVAL = 100;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 14, 11);

    public StormGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(READING, Forecast.Reading.FAIR));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(READING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        if (context.getLevel() instanceof ServerLevel level) {
            state = state.setValue(READING, Forecast.reading(level));
        }
        return state;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide() && !old.is(this)) {
            level.scheduleTick(pos, this, INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refresh(state, level, pos);
        level.scheduleTick(pos, this, INTERVAL);
    }

    /** Sets the reading from the forecast now; returns the state the block ends up in. */
    public static BlockState refresh(BlockState state, ServerLevel level, BlockPos pos) {
        Forecast.Reading now = Forecast.reading(level);
        if (state.getValue(READING) == now) {
            return state;
        }
        BlockState next = state.setValue(READING, now);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.3F, 1.0F);
        level.updateNeighbourForOutputSignal(pos, next.getBlock());
        return next;
    }

    public static Component line(Forecast.Reading reading) {
        return Component.translatable("block.tempestas.storm_glass.reading." + reading.getSerializedName());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState now = level instanceof ServerLevel server ? refresh(state, server, pos) : state;
        player.sendOverlayMessage(line(now.getValue(READING)));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(READING).comparator();
    }
}
