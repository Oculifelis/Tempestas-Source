package dev.tempestas.sky;

import dev.tempestas.Tempestas;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** The jar's caught-sky component and the player's journal attachment. */
public final class SkyData {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Tempestas.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Tempestas.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CaughtSky>> CAUGHT_SKY =
            COMPONENTS.registerComponentType("caught_sky",
                    b -> b.persistent(CaughtSky.CODEC).networkSynchronized(CaughtSky.STREAM_CODEC));

    public static final Supplier<AttachmentType<Journal>> JOURNAL = ATTACHMENTS.register("sky_journal",
            () -> AttachmentType.builder(() -> Journal.EMPTY)
                    .serialize(Journal.CODEC.fieldOf("firsts"))
                    .sync((holder, player) -> holder == player, Journal.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    private SkyData() {
    }
}
