package dev.tempestas.compat;

import dev.tempestas.Tempestas;
import dev.tempestas.block.CondenserBlock;
import dev.tempestas.block.CondenserBlockEntity;
import dev.tempestas.block.StormGlassBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows the condenser's climate reading, what it is making and how far along. The climate is only known on the
 * server, so both lines come across as server data.
 */
@WailaPlugin
public class TempestasJadePlugin implements IWailaPlugin {
    private static final Identifier CONDENSER = Tempestas.id("weather_condenser");
    private static final Identifier STORM_GLASS = Tempestas.id("storm_glass");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Data.INSTANCE, CondenserBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Provider.INSTANCE, CondenserBlock.class);
        registration.registerBlockComponent(GlassProvider.INSTANCE, StormGlassBlock.class);
    }

    public enum Data implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof CondenserBlockEntity condenser) {
                data.put("TPStatus", accessor.encodeAsNbt(ComponentSerialization.STREAM_CODEC, condenser.status()));
                data.put("TPClimate", accessor.encodeAsNbt(ComponentSerialization.STREAM_CODEC, condenser.climateLine()));
            }
        }

        @Override
        public Identifier getUid() {
            return CONDENSER;
        }
    }

    public enum Provider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            for (String key : new String[]{"TPStatus", "TPClimate"}) {
                Tag tag = data.get(key);
                if (tag != null) {
                    accessor.decodeFromNbt(ComponentSerialization.STREAM_CODEC, tag).ifPresent(tooltip::add);
                }
            }
        }

        @Override
        public Identifier getUid() {
            return CONDENSER;
        }
    }

    /** The reading is part of the block state, so the client can show it without server data. */
    public enum GlassProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockState().hasProperty(StormGlassBlock.READING)) {
                tooltip.add(StormGlassBlock.line(accessor.getBlockState().getValue(StormGlassBlock.READING)));
            }
        }

        @Override
        public Identifier getUid() {
            return STORM_GLASS;
        }
    }
}
