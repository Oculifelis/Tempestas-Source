package dev.tempestas.block;

import com.fundamentum.climate.Climate;
import dev.tempestas.TempestasRegistry;
import dev.tempestas.climate.Condensate;
import dev.tempestas.climate.Condensates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Every second it reads the climate at its own spot, picks the condensate that fits, and works toward it. Change the
 * climate and the work starts over. One output slot that hoppers may empty from any side.
 */
public class CondenserBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] OUT = {0};
    private static final int STEP = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private @Nullable Identifier current;
    private int progress;
    private double temperature;
    private double humidity;

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(TempestasRegistry.CONDENSER_BE.get(), pos, state);
    }

    public @Nullable Identifier current() {
        return current;
    }

    public int progress() {
        return progress;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CondenserBlockEntity be) {
        if (level.getGameTime() % STEP != 0L) {
            return;
        }
        be.temperature = Climate.temperature(level, pos);
        be.humidity = Climate.humidity(level, pos);
        Map.Entry<Identifier, Condensate> found = Condensates.at(level, pos);
        Identifier now = found == null ? null : found.getKey();
        boolean changed = false;
        if (!Objects.equals(now, be.current)) {
            be.current = now;
            be.progress = 0;
            changed = true;
            be.setChanged();
        }
        boolean working = false;
        if (found != null) {
            ItemStack result = found.getValue().stack();
            ItemStack out = be.items.get(0);
            boolean fits = out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result)
                    && out.getCount() + result.getCount() <= out.getMaxStackSize());
            if (fits) {
                working = true;
                be.progress += STEP;
                if (be.progress >= found.getValue().time()) {
                    be.progress = 0;
                    if (out.isEmpty()) {
                        be.items.set(0, result);
                    } else {
                        out.grow(result.getCount());
                    }
                    level.updateNeighbourForOutputSignal(pos, state.getBlock());
                }
                be.setChanged();
            }
        }
        if (state.getValue(CondenserBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(CondenserBlock.WORKING, working), 3);
        } else if (changed) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public ItemStack takeOutput() {
        ItemStack out = items.get(0);
        items.set(0, ItemStack.EMPTY);
        setChanged();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        return out;
    }

    /** The line shown when clicked empty-handed and in Jade. */
    public Component status() {
        if (current == null) {
            return Component.translatable("block.tempestas.weather_condenser.idle");
        }
        Condensate c = Condensates.TABLE.get(current);
        int pct = c == null ? 0 : Math.min(100, progress * 100 / c.time());
        Component name = c == null ? Component.literal(current.toString()) : c.stack().getHoverName();
        return Component.translatable("block.tempestas.weather_condenser.working", name, pct);
    }

    public Component climateLine() {
        return Component.translatable("tempestas.climate.short",
                String.format(Locale.ROOT, "%.2f", temperature), String.format(Locale.ROOT, "%.2f", humidity));
    }

    // ---- container -------------------------------------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack s = ContainerHelper.removeItem(items, slot, amount);
        if (!s.isEmpty()) {
            setChanged();
        }
        return s;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return OUT;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    // ---- saving and sync -------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putString("Current", current == null ? "" : current.toString());
        output.putInt("Progress", progress);
        output.putDouble("Temperature", temperature);
        output.putDouble("Humidity", humidity);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        String c = input.getStringOr("Current", "");
        current = c.isEmpty() ? null : Identifier.tryParse(c);
        progress = input.getIntOr("Progress", 0);
        temperature = input.getDoubleOr("Temperature", 0.0D);
        humidity = input.getDoubleOr("Humidity", 0.0D);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, this);
        }
        super.preRemoveSideEffects(pos, state);
    }
}
