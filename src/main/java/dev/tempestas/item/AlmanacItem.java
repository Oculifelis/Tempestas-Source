package dev.tempestas.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The sky journal. Opens the list of skies, caught or not; sneaking opens the guide instead. */
public class AlmanacItem extends Item {
    /** Set by the client entry point (true = sneaking: open the guide); stays a no-op on a dedicated server. */
    public static java.util.function.Consumer<Boolean> OPEN = sneaking -> {
    };

    public AlmanacItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            OPEN.accept(player.isShiftKeyDown());
        }
        return InteractionResult.SUCCESS;
    }
}
