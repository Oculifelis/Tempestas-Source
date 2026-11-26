package dev.tempestas.charm;

import com.fundamentum.climate.Climate;
import com.fundamentum.data.DataTable;
import com.fundamentum.data.DataTables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tempestas.Tempestas;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weather-bound charms, from {@code data/<ns>/tempestas/charms/<name>.json}, keyed by the item. A worn charm only
 * works while its own weather is over the wearer ({@code weather}: clear, rain, snow, thunder; {@code sky}: needs open
 * sky; optional {@code time} window of the day). While it works it gives {@code attributes} and {@code effects}, runs
 * its {@code aura} around the wearer, adds {@code on_hit} effects to whatever the wearer hurts, and with
 * {@code lightning_proof} lightning can't touch the wearer (a strike charges the next hit with lightning instead);
 * with {@code freeze_proof} the wearer never freezes. {@code temperature} min/max (Fundamentum climate) narrows it
 * further, and {@code also_in} lists dimensions where it always works. A pack can retune any of this or turn any item into a charm
 * (it also needs the {@code curios:charm} tag to fit the slot).
 */
public final class Charms {
    public enum Weather { CLEAR, RAIN, SNOW, THUNDER }

    public record Bonus(Identifier attribute, double amount, AttributeModifier.Operation operation) {
    }

    public record Effect(Identifier effect, int amplifier, int seconds) {
    }

    /** type: ignite (seconds), freeze (ticks), lightning (chance), effect (effect, amplifier, seconds). */
    public record OnHit(String type, double value, Effect effect) {
    }

    /** type: frost_walk (radius), grow (radius, tries). */
    public record Aura(String type, int radius, int tries) {
    }

    public record Charm(Identifier item, List<Weather> weather, boolean sky, int timeFrom, int timeTo,
                        double minTemperature, double maxTemperature, List<Identifier> dimensions,
                        List<Bonus> bonuses, List<Effect> effects, List<OnHit> onHit, List<Aura> aura,
                        boolean lightningProof, boolean freezeProof) {
        static Charm parse(JsonObject json) {
            List<Weather> weather = new ArrayList<>();
            JsonElement w = json.get("weather");
            if (w != null && w.isJsonArray()) {
                for (JsonElement e : w.getAsJsonArray()) {
                    weather.add(Weather.valueOf(e.getAsString().toUpperCase(Locale.ROOT)));
                }
            } else if (w != null) {
                weather.add(Weather.valueOf(w.getAsString().toUpperCase(Locale.ROOT)));
            }
            JsonObject time = GsonHelper.getAsJsonObject(json, "time", new JsonObject());
            List<Bonus> bonuses = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "attributes", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                bonuses.add(new Bonus(Identifier.parse(GsonHelper.getAsString(o, "attribute")),
                        GsonHelper.getAsDouble(o, "amount"), operation(GsonHelper.getAsString(o, "operation", "add_value"))));
            }
            List<Effect> effects = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "effects", new JsonArray())) {
                effects.add(effect(e.getAsJsonObject()));
            }
            List<OnHit> onHit = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "on_hit", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                String type = GsonHelper.getAsString(o, "type");
                double value = switch (type) {
                    case "ignite" -> GsonHelper.getAsDouble(o, "seconds", 3);
                    case "freeze" -> GsonHelper.getAsDouble(o, "ticks", 160);
                    case "lightning" -> GsonHelper.getAsDouble(o, "chance", 0.2);
                    default -> 0;
                };
                onHit.add(new OnHit(type, value, type.equals("effect") ? effect(o) : null));
            }
            List<Aura> aura = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "aura", new JsonArray())) {
                JsonObject o = e.getAsJsonObject();
                aura.add(new Aura(GsonHelper.getAsString(o, "type"), Mth.clamp(GsonHelper.getAsInt(o, "radius", 2), 1, 8),
                        Mth.clamp(GsonHelper.getAsInt(o, "tries", 2), 1, 16)));
            }
            JsonObject t = GsonHelper.getAsJsonObject(json, "temperature", new JsonObject());
            List<Identifier> dimensions = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "also_in", new JsonArray())) {
                dimensions.add(Identifier.parse(e.getAsString()));
            }
            return new Charm(Identifier.parse(GsonHelper.getAsString(json, "item")), List.copyOf(weather),
                    GsonHelper.getAsBoolean(json, "sky", true), GsonHelper.getAsInt(time, "from", 0),
                    GsonHelper.getAsInt(time, "to", 24000), GsonHelper.getAsDouble(t, "min", -Double.MAX_VALUE),
                    GsonHelper.getAsDouble(t, "max", Double.MAX_VALUE), List.copyOf(dimensions),
                    List.copyOf(bonuses), List.copyOf(effects), List.copyOf(onHit), List.copyOf(aura),
                    GsonHelper.getAsBoolean(json, "lightning_proof", false),
                    GsonHelper.getAsBoolean(json, "freeze_proof", false));
        }

        private static Effect effect(JsonObject o) {
            return new Effect(Identifier.parse(GsonHelper.getAsString(o, "effect")), GsonHelper.getAsInt(o, "amplifier", 0),
                    GsonHelper.getAsInt(o, "seconds", 3));
        }
    }

    private static final String CHARGED = "tempestas_storm_charged";

    public static final DataTable<Charm> TABLE = DataTables
            .register(Tempestas.id("charms"), "tempestas/charms", Charm::parse)
            .keyField("item")
            .synced();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Charms::tick);
        NeoForge.EVENT_BUS.addListener(Charms::hit);
        NeoForge.EVENT_BUS.addListener(Charms::struck);
        NeoForge.EVENT_BUS.addListener(Charms::tooltip);
    }

    /** True while the item is worn and its weather is over the wearer. Server side. */
    public static boolean active(ServerPlayer player, Charm charm) {
        Item item = BuiltInRegistries.ITEM.getValue(charm.item());
        if (!CuriosApi.getCuriosInventory(player).map(inv -> inv.isEquipped(item)).orElse(false)) {
            return false;
        }
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        if (charm.dimensions().contains(level.dimension().identifier())) {
            return true;
        }
        if (charm.sky() && !level.canSeeSky(pos.above())) {
            return false;
        }
        int time = (int) Math.floorMod(level.getDefaultClockTime(), 24000L);
        boolean inWindow = charm.timeFrom() <= charm.timeTo()
                ? time >= charm.timeFrom() && time <= charm.timeTo()
                : time >= charm.timeFrom() || time <= charm.timeTo();
        if (!inWindow) {
            return false;
        }
        if (charm.minTemperature() > -Double.MAX_VALUE || charm.maxTemperature() < Double.MAX_VALUE) {
            double temperature = Climate.temperature(level, pos);
            if (temperature < charm.minTemperature() || temperature > charm.maxTemperature()) {
                return false;
            }
        }
        if (charm.weather().isEmpty()) {
            return true;
        }
        Biome.Precipitation p = level.isRaining()
                ? level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) : Biome.Precipitation.NONE;
        for (Weather w : charm.weather()) {
            boolean match = switch (w) {
                case CLEAR -> p == Biome.Precipitation.NONE;
                case RAIN -> p == Biome.Precipitation.RAIN;
                case SNOW -> p == Biome.Precipitation.SNOW;
                case THUNDER -> level.isThundering() && p != Biome.Precipitation.NONE;
            };
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) {
            return;
        }
        for (Map.Entry<Identifier, Charm> entry : TABLE.entries().entrySet()) {
            Charm charm = entry.getValue();
            boolean on = active(player, charm);
            int i = 0;
            for (Bonus bonus : charm.bonuses()) {
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(bonus.attribute()).orElse(null);
                Identifier id = modifierId(charm, i++);
                AttributeInstance inst = attribute == null ? null : player.getAttribute(attribute);
                if (inst == null) {
                    continue;
                }
                if (on) {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(id, bonus.amount(), bonus.operation()));
                } else {
                    inst.removeModifier(id);
                }
            }
            if (!on) {
                continue;
            }
            if (charm.freezeProof()) {
                player.setTicksFrozen(0);
            }
            for (Effect e : charm.effects()) {
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(e.effect()).orElse(null);
                if (effect == null) {
                    continue;
                }
                MobEffectInstance current = player.getEffect(effect);
                if (current == null || current.getDuration() < 30) {
                    player.addEffect(new MobEffectInstance(effect, 100, e.amplifier(), true, false, true));
                }
            }
            for (Aura aura : charm.aura()) {
                aura(player, aura);
            }
        }
    }

    private static void aura(ServerPlayer player, Aura aura) {
        ServerLevel level = player.level();
        RandomSource random = level.getRandom();
        switch (aura.type()) {
            case "frost_walk" -> {
                if (!player.onGround()) {
                    return;
                }
                BlockPos below = player.blockPosition().below();
                int r = aura.radius();
                BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
                for (BlockPos pos : BlockPos.betweenClosed(below.offset(-r, 0, -r), below.offset(r, 0, r))) {
                    if (pos.distSqr(below) > (double) (r * r) + 0.5) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(pos.above()).isAir()) {
                        level.setBlockAndUpdate(pos, ice);
                        level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(random, 60, 120));
                    }
                }
            }
            case "grow" -> {
                int r = aura.radius();
                for (int i = 0; i < aura.tries(); i++) {
                    BlockPos pos = player.blockPosition().offset(random.nextInt(2 * r + 1) - r, random.nextInt(3) - 1,
                            random.nextInt(2 * r + 1) - r);
                    BlockState state = level.getBlockState(pos);
                    if ((state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) && state.isRandomlyTicking()) {
                        state.randomTick(level, pos, random);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void hit(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getInflictedDamage() <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target == player) {
            return;
        }
        if (player.getPersistentData().getBooleanOr(CHARGED, false)) {
            player.getPersistentData().remove(CHARGED);
            bolt(player, target);
        }
        for (Charm charm : TABLE.entries().values()) {
            if (charm.onHit().isEmpty() || !active(player, charm)) {
                continue;
            }
            for (OnHit h : charm.onHit()) {
                switch (h.type()) {
                    case "ignite" -> target.igniteForSeconds((float) h.value());
                    case "freeze" -> target.setTicksFrozen(Math.max(target.getTicksFrozen(), (int) h.value()));
                    case "lightning" -> {
                        ServerLevel level = player.level();
                        if (level.getRandom().nextDouble() < h.value() && level.canSeeSky(target.blockPosition())) {
                            bolt(player, target);
                        }
                    }
                    case "effect" -> BuiltInRegistries.MOB_EFFECT.get(h.effect().effect()).ifPresent(effect ->
                            target.addEffect(new MobEffectInstance(effect, h.effect().seconds() * 20, h.effect().amplifier()), player));
                    default -> {
                    }
                }
            }
        }
    }

    private static void struck(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (Charm charm : TABLE.entries().values()) {
            if (charm.lightningProof() && active(player, charm)) {
                event.setCanceled(true);
                // The wearer's own bolts (from a charged hit) never recharge the charm, or every hit would chain into the next.
                if (event.getLightning().getCause() != player && !player.getPersistentData().getBooleanOr(CHARGED, false)) {
                    player.getPersistentData().putBoolean(CHARGED, true);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(),
                            SoundSource.PLAYERS, 0.6f, 1.6f);
                }
                return;
            }
        }
    }

    private static void tooltip(ItemTooltipEvent event) {
        Charm charm = TABLE.get(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()));
        if (charm == null) {
            return;
        }
        List<Component> lines = event.getToolTip();
        for (Weather w : charm.weather()) {
            lines.add(Component.translatable("tooltip.tempestas.charm.weather." + w.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        }
        String desc = "tooltip." + charm.item().getNamespace() + ".charm." + charm.item().getPath();
        lines.add(Component.translatable(desc).withStyle(ChatFormatting.BLUE));
    }

    private static void bolt(ServerPlayer player, LivingEntity target) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(player.level(), EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(Vec3.atBottomCenterOf(target.blockPosition()));
            bolt.setCause(player);
            player.level().addFreshEntity(bolt);
        }
    }

    private static Identifier modifierId(Charm charm, int i) {
        return Tempestas.id("charm/" + charm.item().getNamespace() + "/" + charm.item().getPath() + "/" + i);
    }

    private static AttributeModifier.Operation operation(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }

    private Charms() {
    }
}
