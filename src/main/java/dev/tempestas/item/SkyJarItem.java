package dev.tempestas.item;

import dev.tempestas.TempestasRegistry;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.Journal;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.Sky;
import dev.tempestas.sky.SkyData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;

import java.util.Map;

/** An empty jar. Opened anywhere, it catches the sky over that spot, if that sky has a name. */
public class SkyJarItem extends Item {
    public SkyJarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        BlockPos pos = BlockPos.containing(player.getEyePosition());
        Map.Entry<Identifier, Sky> found = Skies.at(server, pos);
        if (found == null) {
            player.sendOverlayMessage(Component.translatable("sky.tempestas.empty").withStyle(ChatFormatting.GRAY));
            player.getCooldowns().addCooldown(held, 20);
            return InteractionResult.FAIL;
        }
        CaughtSky caught = Skies.record(server, pos, found.getKey());
        ItemStack jar = new ItemStack(TempestasRegistry.CAUGHT_SKY.get());
        jar.set(SkyData.CAUGHT_SKY.get(), caught);
        ItemStack result = ItemUtils.createFilledResult(held, player, jar, false);
        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 1.0F, 1.0F);

        // A pack can tie an advancement to any sky: <sky namespace>:caught/<sky path>, criterion "caught".
        // Not for fake players (machines using the jar): they would get an advancement file of their own.
        if (player instanceof ServerPlayer sp && !sp.isFakePlayer()) {
            AdvancementHolder adv = server.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(
                    caught.sky().getNamespace(), "caught/" + caught.sky().getPath()));
            if (adv != null) {
                sp.getAdvancements().award(adv, "caught");
            }
        }

        Journal journal = player.getData(SkyData.JOURNAL);
        if (!journal.has(caught.sky())) {
            player.setData(SkyData.JOURNAL, journal.with(caught));
            player.sendSystemMessage(Component.translatable("sky.tempestas.new_entry", Skies.name(caught.sky()))
                    .withStyle(ChatFormatting.AQUA));
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            player.sendOverlayMessage(Skies.name(caught.sky()).copy().withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(result);
    }
}
