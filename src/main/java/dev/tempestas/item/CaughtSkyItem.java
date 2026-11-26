package dev.tempestas.item;

import dev.tempestas.TempestasRegistry;
import dev.tempestas.sky.CaughtSky;
import dev.tempestas.sky.Releases;
import dev.tempestas.sky.Skies;
import dev.tempestas.sky.Sky;
import dev.tempestas.sky.SkyData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A jar holding one sky. Opening it lets that weather out over a small patch for a while, and gives the jar back. */
public class CaughtSkyItem extends Item {
    public CaughtSkyItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        CaughtSky c = stack.get(SkyData.CAUGHT_SKY.get());
        return c == null ? super.getName(stack) : Component.translatable("item.tempestas.caught_sky.named", Skies.name(c.sky()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> out, TooltipFlag flag) {
        CaughtSky c = stack.get(SkyData.CAUGHT_SKY.get());
        if (c != null) {
            out.accept(Skies.provenance(c).copy().withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        CaughtSky c = held.get(SkyData.CAUGHT_SKY.get());
        Sky sky = c == null ? null : Skies.TABLE.get(c.sky());
        if (sky == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Releases.release(server, player.blockPosition(), c.sky(), sky);
        }
        if (player.hasInfiniteMaterials()) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS.heldItemTransformedTo(new ItemStack(TempestasRegistry.SKY_JAR.get()));
    }
}
