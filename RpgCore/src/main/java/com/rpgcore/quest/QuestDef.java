package com.rpgcore.quest;

import com.rpgcore.logic.QuestFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * A hand-made quest line from data/&lt;ns&gt;/rpgcore/quests/*.json (§13). Objective types: talk, reach, kill_specific
 * (named bosses/elites only, never "kill N"), find_item, interact_block, event_outcome.
 */
public record QuestDef(ResourceLocation id, String title, List<Step> steps, QuestFlow flow, Rewards rewards) {

    /**
     * @param text  journal line for this step (translation key)
     * @param nav   navigation target, or null
     * @param choiceTexts choice id -> translation key, shown as dialogue options on talk steps
     */
    public record Step(String id, Objective objective, String text, BlockPos nav, String next,
                       Map<String, String> choices, Map<String, String> choiceTexts) {}

    /**
     * @param target npc id (talk), entity/boss id (kill_specific), item id (find_item), block id (interact_block),
     *               event id (event_outcome), location id (reach)
     * @param pos    reach center / optional exact block for interact_block
     * @param value  outcome for event_outcome
     */
    public record Objective(String type, String target, BlockPos pos, int radius, int count, String value, boolean consume) {}

    public record Rewards(List<ItemStack> items, int xp, ResourceLocation lootSource) {
        public static final Rewards NONE = new Rewards(List.of(), 0, null);
    }

    public Step step(String id) {
        for (Step s : steps) if (s.id().equals(id)) return s;
        return null;
    }

    public String titleKey() {
        return title;
    }
}
