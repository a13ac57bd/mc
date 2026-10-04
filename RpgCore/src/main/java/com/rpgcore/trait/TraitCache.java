package com.rpgcore.trait;

import com.rpgcore.compat.CuriosCompat;
import com.rpgcore.item.ItemData;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Trigger;
import com.rpgcore.trait.effect.Effect;
import com.rpgcore.trait.effect.Effects;
import com.rpgcore.util.Attr;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traits currently active on each player, grouped by trigger (§5.3 steps 1-2). Recomputed on equipment changes;
 * passive attribute effects are applied/removed here.
 */
public final class TraitCache {
    private TraitCache() {}

    public record ActiveRule(ActiveTrait trait, TraitDef.Rule rule) {}

    private record Applied(Attribute attribute, UUID id) {}

    public static final class PlayerTraits {
        private final Map<Trigger, List<ActiveRule>> byTrigger = new EnumMap<>(Trigger.class);
        private final List<ActiveTrait> all = new ArrayList<>();
        private final List<Applied> applied = new ArrayList<>();

        public List<ActiveRule> rules(Trigger trigger) {
            return byTrigger.getOrDefault(trigger, Collections.emptyList());
        }

        public List<ActiveTrait> all() {
            return all;
        }

        public boolean isEmpty() {
            return all.isEmpty();
        }

        public boolean hasTrait(ResourceLocation id) {
            for (ActiveTrait t : all) if (t.def().id().equals(id)) return true;
            return false;
        }
    }

    private static final Map<UUID, PlayerTraits> CACHE = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();

    public static PlayerTraits get(ServerPlayer player) {
        PlayerTraits pt = CACHE.get(player.getUUID());
        if (pt == null || DIRTY.remove(player.getUUID())) pt = recompute(player);
        return pt;
    }

    public static void markDirty(ServerPlayer player) {
        DIRTY.add(player.getUUID());
    }

    public static boolean isDirty(ServerPlayer player) {
        return DIRTY.contains(player.getUUID());
    }

    /** After /reload every cache entry is stale. */
    public static void invalidateAll() {
        DIRTY.addAll(CACHE.keySet());
    }

    public static void remove(ServerPlayer player) {
        PlayerTraits old = CACHE.remove(player.getUUID());
        DIRTY.remove(player.getUUID());
        if (old != null) removeApplied(player, old);
    }

    /** Equipped stacks that contribute traits: main hand weapon, four armor slots, Curios slots. */
    public static List<Map.Entry<String, ItemStack>> equipped(ServerPlayer player) {
        List<Map.Entry<String, ItemStack>> out = new ArrayList<>();
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof ArmorItem)) out.add(Map.entry("mainhand", main));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            out.add(Map.entry(slot.getName(), player.getItemBySlot(slot)));
        }
        out.addAll(CuriosCompat.equipped(player));
        return out;
    }

    public static PlayerTraits recompute(ServerPlayer player) {
        PlayerTraits old = CACHE.get(player.getUUID());
        if (old != null) removeApplied(player, old);
        PlayerTraits pt = new PlayerTraits();
        for (Map.Entry<String, ItemStack> e : equipped(player)) {
            ItemStack stack = e.getValue();
            if (!ItemData.has(stack)) continue;
            int tier = ItemData.tier(stack);
            for (ItemData.TraitRoll roll : ItemData.traits(stack)) {
                TraitDef def = TraitRegistry.get(roll.id());
                if (def != null) add(pt, new ActiveTrait(def, roll.roll() * LootMath.tierMultiplier(tier), tier, e.getKey()));
            }
            TraitDef ins = TraitRegistry.get(ItemData.inscription(stack));
            if (ins != null) add(pt, new ActiveTrait(ins, ins.rollMax() * LootMath.tierMultiplier(tier), tier, e.getKey() + "/inscription"));
        }
        applyPassives(player, pt);
        CACHE.put(player.getUUID(), pt);
        DIRTY.remove(player.getUUID());
        return pt;
    }

    private static void add(PlayerTraits pt, ActiveTrait trait) {
        pt.all.add(trait);
        for (TraitDef.Rule rule : trait.def().rules()) {
            if (rule.trigger() == null) continue;
            pt.byTrigger.computeIfAbsent(rule.trigger(), k -> new ArrayList<>()).add(new ActiveRule(trait, rule));
        }
    }

    private static void applyPassives(ServerPlayer player, PlayerTraits pt) {
        int index = 0;
        for (ActiveRule ar : pt.rules(Trigger.PASSIVE)) {
            for (Effect effect : ar.rule().effects()) {
                if (!(effect instanceof Effects.AttributeEffect attr)) continue;
                UUID id = TraitStacks.modifierId("passive/" + ar.trait().def().id() + "/" + ar.trait().slot() + "/" + index++);
                Attr.setTransient(player, attr.attribute(), id, "rpgcore passive", attr.amount().get(ar.trait()), attr.operation());
                pt.applied.add(new Applied(attr.attribute(), id));
            }
        }
    }

    private static void removeApplied(ServerPlayer player, PlayerTraits pt) {
        for (Applied a : pt.applied) Attr.remove(player, a.attribute(), a.id());
        pt.applied.clear();
    }
}
