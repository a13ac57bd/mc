package com.rpgcore.events;

import com.rpgcore.ecology.SpawnTables;
import com.rpgcore.logic.EventMachine;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * A world event from data/&lt;ns&gt;/rpgcore/events/*.json (§10).
 *
 * @param trigger progress flag that starts it (#14: boss_killed / chapter_done / location_discovered / quest_done)
 */
public record EventDef(ResourceLocation id, String trigger, Region region, EventMachine.Def machine,
                       Map<String, Effects> effects, Map<String, Goal> goals, Map<String, ResourceLocation> rewards) {

    /** Either a town (resolved through TownSavedData) or an explicit circle. */
    public record Region(ResourceLocation town, ResourceLocation dimension, int x, int z, int radius) {}

    /**
     * What a state does while active. Hints instead of notifications: NPC dialogue, distant smoke, refugees.
     *
     * @param danger     added to the danger tier inside the region
     * @param spawns     extra spawn table while active (null = none)
     * @param hideNpcs   town NPCs are stored away
     * @param boss       event boss spawned when the state starts (null = none)
     * @param dialogue   translation key NPCs of the town say while this state is active
     */
    public record Effects(int danger, SpawnTables.TableDef spawns, boolean hideNpcs, ResourceLocation boss, boolean smoke,
                          int refugees, boolean corruption, String dialogue) {
        public static final Effects NONE = new Effects(0, null, false, null, false, 0, false, null);
    }

    /** Player intervention: kill_specific (boss/elite or entity id) or quest_done. */
    public record Goal(String type, ResourceLocation id) {}
}
