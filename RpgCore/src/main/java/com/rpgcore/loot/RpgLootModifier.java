package com.rpgcore.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcore.boss.RpgBoss;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.danger.Elites;
import com.rpgcore.danger.MobScaling;
import com.rpgcore.registry.RpgTags;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * The one GlobalLootModifier rpgcore:rpg_loot (§6). Player kills -> mob / elite / boss; chests/* opened by a
 * player -> chest. Original drops are kept; rpgcore loot is added.
 */
public class RpgLootModifier extends LootModifier {
    public static final Supplier<Codec<RpgLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, RpgLootModifier::new)));

    public RpgLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        if (table == null) return loot;
        Entity self = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        String path = table.getPath();
        if (path.startsWith("entities/")) {
            if (!(self instanceof LivingEntity victim)) return loot;
            if (!(context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer killer)) return loot;
            ResourceLocation source;
            ResourceLocation bossId = null;
            if (victim.getType().is(RpgTags.BOSS)) {
                source = LootSources.BOSS;
                bossId = victim instanceof RpgBoss boss ? boss.bossId() : ForgeRegistries.ENTITY_TYPES.getKey(victim.getType());
            } else if (Elites.isElite(victim)) {
                source = LootSources.ELITE;
            } else if (victim instanceof Enemy) {
                source = LootSources.MOB;
            } else {
                return loot;
            }
            int tier = MobScaling.tier(victim);
            loot.addAll(LootSources.roll(source, killer, tier, context.getRandom(), bossId));
        } else if (path.startsWith("chests/")) {
            if (!(self instanceof ServerPlayer opener)) return loot;
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            BlockPos pos = origin == null ? opener.blockPosition() : BlockPos.containing(origin);
            int tier = DangerMap.get(context.getLevel(), pos);
            loot.addAll(LootSources.roll(LootSources.CHEST, opener, tier, context.getRandom(), null));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
