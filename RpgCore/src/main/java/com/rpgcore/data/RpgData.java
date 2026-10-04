package com.rpgcore.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.rpgcore.RpgCore;
import com.rpgcore.boss.BossDefs;
import com.rpgcore.danger.DangerConfig;
import com.rpgcore.ecology.Factions;
import com.rpgcore.ecology.SpawnTables;
import com.rpgcore.ecology.Squads;
import com.rpgcore.events.EventDefs;
import com.rpgcore.forge.Inscriptions;
import com.rpgcore.home.Blueprints;
import com.rpgcore.loot.Bases;
import com.rpgcore.loot.LootSources;
import com.rpgcore.loot.Uniques;
import com.rpgcore.quest.Chapters;
import com.rpgcore.quest.Dialogues;
import com.rpgcore.quest.QuestDefs;
import com.rpgcore.rift.RiftConfig;
import com.rpgcore.town.TownDefs;
import com.rpgcore.trait.TraitRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One reload listener for every JSON under data/&lt;ns&gt;/rpgcore/. The first path segment selects the subsystem,
 * e.g. data/rpgcore/rpgcore/traits/void_hunter.json -> traits, id rpgcore:void_hunter.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class RpgData extends SimpleJsonResourceReloadListener {
    public static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final RpgData INSTANCE = new RpgData();

    private RpgData() {
        super(GSON, "rpgcore");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> all, ResourceManager resourceManager, ProfilerFiller profiler) {
        // order matters: uniques/inscriptions/bases reference traits
        run("traits", () -> TraitRegistry.load(sub(all, "traits")));
        run("uniques", () -> Uniques.load(sub(all, "uniques")));
        run("inscriptions", () -> Inscriptions.load(sub(all, "inscriptions")));
        run("bases", () -> Bases.load(sub(all, "bases")));
        run("loot_sources", () -> LootSources.load(sub(all, "loot_sources")));
        run("danger", () -> DangerConfig.load(sub(all, "danger")));
        run("factions", () -> Factions.load(sub(all, "factions")));
        run("squads", () -> Squads.load(sub(all, "squads")));
        run("spawns", () -> SpawnTables.load(sub(all, "spawns")));
        run("bosses", () -> BossDefs.load(sub(all, "bosses")));
        run("rift", () -> RiftConfig.load(sub(all, "rift")));
        run("events", () -> EventDefs.load(sub(all, "events")));
        run("towns", () -> TownDefs.load(sub(all, "towns")));
        run("dialogues", () -> Dialogues.load(sub(all, "dialogues")));
        run("quests", () -> QuestDefs.load(sub(all, "quests")));
        run("chapters", () -> Chapters.load(sub(all, "chapters"), root(all, "endings")));
        run("blueprints", () -> Blueprints.load(sub(all, "blueprints")));
        RpgCore.LOG.info("rpgcore data loaded: {} files", all.size());
    }

    private static void run(String what, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            RpgCore.LOG.error("rpgcore: failed to load {}", what, e);
        }
    }

    /** Entries under {@code folder/}, re-keyed without the folder. */
    public static Map<ResourceLocation, JsonElement> sub(Map<ResourceLocation, JsonElement> all, String folder) {
        String prefix = folder + "/";
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        all.forEach((id, json) -> {
            if (id.getPath().startsWith(prefix)) {
                out.put(new ResourceLocation(id.getNamespace(), id.getPath().substring(prefix.length())), json);
            }
        });
        return out;
    }

    /** Files directly at data/&lt;ns&gt;/rpgcore/&lt;name&gt;.json. */
    public static Map<ResourceLocation, JsonElement> root(Map<ResourceLocation, JsonElement> all, String name) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        all.forEach((id, json) -> {
            if (id.getPath().equals(name)) out.put(id, json);
        });
        return out;
    }

    /** Runs {@code loader} for each entry and logs (instead of throwing) bad files. */
    public static void each(String kind, Map<ResourceLocation, JsonElement> files, Entry loader) {
        files.forEach((id, json) -> {
            try {
                loader.accept(id, json);
            } catch (Exception e) {
                RpgCore.LOG.error("rpgcore: bad {} {}: {}", kind, id, e.toString());
            }
        });
    }

    @FunctionalInterface
    public interface Entry {
        void accept(ResourceLocation id, JsonElement json) throws Exception;
    }
}
