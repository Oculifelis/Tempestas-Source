package dev.tempestas.block;

import com.mojang.serialization.MapCodec;
import dev.tempestas.TempestasRegistry;
import dev.tempestas.climate.Condensate;
import dev.tempestas.climate.Condensates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a condensate out of the air. Which one depends on the climate where it stands; click it to take what it made.
 */
public class CondenserBlock extends BaseEntityBlock {
    public static final MapCodec<CondenserBlock> CODEC = simpleCodec(CondenserBlock::new);
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    /** Base plate, tank with its posts, cap and vent, matching the block model. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(2, 3, 2, 14, 12, 14),
            Block.box(2, 12, 2, 14, 14, 14),
            Block.box(6, 14, 6, 10, 16, 10));

    public CondenserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WORKING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CondenserBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                           BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, TempestasRegistry.CONDENSER_BE.get(), CondenserBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CondenserBlockEntity condenser)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack out = condenser.takeOutput();
        if (out.isEmpty()) {
            player.sendOverlayMessage(condenser.status());
        } else if (!player.getInventory().add(out)) {
            player.drop(out, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || random.nextInt(3) != 0
                || !(level.getBlockEntity(pos) instanceof CondenserBlockEntity condenser)) {
            return;
        }
        Condensate c = condenser.current() == null ? null : Condensates.TABLE.get(condenser.current());
        int colour = c == null ? 0xFFFFFF : c.color();
        // A bead of the condensate's colour gathers on the glass and sinks.
        level.addParticle(new DustParticleOptions(colour, 0.8F),
                pos.getX() + 0.2D + random.nextDouble() * 0.6D, pos.getY() + 0.55D + random.nextDouble() * 0.35D,
                pos.getZ() + 0.2D + random.nextDouble() * 0.6D, 0.0D, -0.02D, 0.0D);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }
}
