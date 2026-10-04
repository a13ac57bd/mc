package com.rpgcore.test;

import com.rpgcore.boss.BossDefs;
import com.rpgcore.combat.Scaling;
import com.rpgcore.events.EventDefs;
import com.rpgcore.home.Blueprints;
import com.rpgcore.loot.Bases;
import com.rpgcore.loot.LootSources;
import com.rpgcore.loot.Uniques;
import com.rpgcore.quest.QuestDefs;
import com.rpgcore.registry.RpgTags;
import com.rpgcore.trait.TraitRegistry;
import com.rpgcore.util.Attr;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * In-pack self test (§18 開服自測): data counts, and every living entity type of the whole pack spawned once to check
 * the x5 health scale and full health on spawn.
 */
public final class SelfTest {
    private SelfTest() {}

    public static List<String> run(ServerLevel level) {
        List<String> out = new ArrayList<>();
        out.add("data: traits=" + TraitRegistry.all().size() + " uniques=" + Uniques.all().size() + " bases=" + Bases.all().size()
                + " sources=" + LootSources.all().size() + " bosses=" + BossDefs.all().size() + " events=" + EventDefs.all().size()
                + " quests=" + QuestDefs.all().size() + " blueprints=" + Blueprints.all().size());
        BlockPos at = new BlockPos(level.getSharedSpawnPos().getX(), level.getMaxBuildHeight() - 8, level.getSharedSpawnPos().getZ());
        int checked = 0;
        List<String> bad = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (type.getCategory() == MobCategory.MISC && type != EntityType.VILLAGER && type != EntityType.IRON_GOLEM) continue;
            Entity e;
            try {
                e = type.create(level);
            } catch (RuntimeException ex) {
                bad.add(ForgeRegistries.ENTITY_TYPES.getKey(type) + ": create failed " + ex.getMessage());
                continue;
            }
            if (!(e instanceof LivingEntity living) || e instanceof Player) {
                if (e != null) e.discard();
                continue;
            }
            living.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
            if (!level.addFreshEntity(living)) continue;
            checked++;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            boolean exempt = type.is(RpgTags.NO_HEALTH_SCALE);
            boolean scaled = exempt || Attr.has(living, Attributes.MAX_HEALTH, Scaling.HEALTH_ID);
            boolean full = living.getHealth() >= living.getMaxHealth() - 0.01F;
            if (!scaled || !full) bad.add(id + ": scaled=" + scaled + " full=" + full + " (" + living.getHealth() + "/" + living.getMaxHealth() + ")");
            living.discard();
        }
        out.add("entities checked: " + checked + ", problems: " + bad.size());
        out.addAll(bad.subList(0, Math.min(20, bad.size())));
        return out;
    }
}
