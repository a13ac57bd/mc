package com.rpgcore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.rpgcore.RpgCore;
import com.rpgcore.ai.TimedGoal;
import com.rpgcore.boss.BossDef;
import com.rpgcore.boss.BossDefs;
import com.rpgcore.boss.RpgBoss;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.danger.Elites;
import com.rpgcore.events.EventDefs;
import com.rpgcore.events.ProgressSavedData;
import com.rpgcore.events.WorldEvents;
import com.rpgcore.home.BlueprintItem;
import com.rpgcore.home.Blueprints;
import com.rpgcore.home.CivilizationScrollItem;
import com.rpgcore.home.Unlocks;
import com.rpgcore.logic.EventMachine;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Rarity;
import com.rpgcore.loot.Generator;
import com.rpgcore.loot.LootSources;
import com.rpgcore.loot.Uniques;
import com.rpgcore.quest.Chapters;
import com.rpgcore.quest.QuestDefs;
import com.rpgcore.quest.QuestLog;
import com.rpgcore.quest.Quests;
import com.rpgcore.registry.RpgEntities;
import com.rpgcore.registry.RpgItems;
import com.rpgcore.rift.RiftInstance;
import com.rpgcore.rift.RiftManager;
import com.rpgcore.test.CastleCheck;
import com.rpgcore.test.SelfTest;
import com.rpgcore.town.NpcEntity;
import com.rpgcore.town.TownDefs;
import com.rpgcore.town.TownSavedData;
import com.rpgcore.util.Inv;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** /rpgcore debug and content commands (permission level 2). */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class RpgCommands {
    private RpgCommands() {}

    private static final SuggestionProvider<CommandSourceStack> UNIQUES = (c, b) -> SharedSuggestionProvider.suggestResource(Uniques.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> SOURCES = (c, b) -> SharedSuggestionProvider.suggestResource(LootSources.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) -> SharedSuggestionProvider.suggestResource(EventDefs.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (c, b) -> SharedSuggestionProvider.suggestResource(QuestDefs.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> BOSSES = (c, b) -> SharedSuggestionProvider.suggestResource(BossDefs.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> TOWNS = (c, b) -> SharedSuggestionProvider.suggestResource(TownDefs.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> BLUEPRINTS = (c, b) -> SharedSuggestionProvider.suggestResource(Blueprints.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> RARITIES = (c, b) -> SharedSuggestionProvider.suggest(List.of("common", "uncommon", "rare"), b);

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static int tierArg(CommandContext<CommandSourceStack> c, int def) {
        try {
            return IntegerArgumentType.getInteger(c, "tier");
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static void ok(CommandContext<CommandSourceStack> c, Supplier<Component> msg) {
        c.getSource().sendSuccess(msg, false);
    }

    private static int localTier(ServerPlayer p) {
        return DangerMap.get(p.level(), p.blockPosition());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rpgcore").requires(s -> s.hasPermission(2))
                // ---- loot ----
                .then(Commands.literal("unique").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(UNIQUES)
                        .executes(c -> unique(c, -1))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(c -> unique(c, tierArg(c, 1))))))
                .then(Commands.literal("loot").then(Commands.argument("source", ResourceLocationArgument.id()).suggests(SOURCES)
                        .executes(c -> loot(c, -1))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(c -> loot(c, tierArg(c, 1))))))
                .then(Commands.literal("gear").then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
                        .executes(c -> gear(c, -1))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(c -> gear(c, tierArg(c, 1))))))
                .then(Commands.literal("danger").executes(RpgCommands::danger))
                .then(Commands.literal("elite").executes(RpgCommands::elite))
                .then(Commands.literal("aitime").executes(c -> {
                    double ms = TimedGoal.takeMillis();
                    ok(c, () -> Component.literal(String.format(Locale.ROOT, "rpgcore AI time since last call: %.2f ms", ms)));
                    return 1;
                }))
                // ---- bosses ----
                .then(Commands.literal("boss").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(BOSSES).executes(RpgCommands::boss)))
                // ---- rifts ----
                .then(Commands.literal("rift")
                        .then(Commands.literal("open").executes(c -> rift(c, -1))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(c -> rift(c, tierArg(c, 1)))))
                        .then(Commands.literal("list").executes(c -> {
                            for (RiftInstance i : RiftManager.instances().values()) {
                                ok(c, () -> Component.literal("cell " + i.cell + " tier " + i.tier + " floor " + i.floor + " " + i.ticksLeft / 20 + "s " + i.phase));
                            }
                            return RiftManager.instances().size();
                        })))
                // ---- world events ----
                .then(Commands.literal("event")
                        .then(Commands.literal("start").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(EVENTS).executes(c -> {
                            EventMachine.StartResult r = WorldEvents.start(ResourceLocationArgument.getId(c, "id"));
                            ok(c, () -> Component.literal("event: " + r));
                            return 1;
                        })))
                        .then(Commands.literal("advance").then(Commands.argument("days", IntegerArgumentType.integer(1, 100)).executes(c -> {
                            WorldEvents.advanceDays(IntegerArgumentType.getInteger(c, "days"));
                            return status(c);
                        })))
                        .then(Commands.literal("goal").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(EVENTS).executes(c -> {
                            WorldEvents.goal(ResourceLocationArgument.getId(c, "id"));
                            return status(c);
                        })))
                        .then(Commands.literal("force").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(EVENTS)
                                .then(Commands.argument("state", StringArgumentType.word()).executes(c -> {
                                    WorldEvents.force(ResourceLocationArgument.getId(c, "id"), StringArgumentType.getString(c, "state"));
                                    return status(c);
                                }))))
                        .then(Commands.literal("status").executes(RpgCommands::status)))
                .then(Commands.literal("progress")
                        .then(Commands.literal("set").then(Commands.argument("flag", StringArgumentType.greedyString()).executes(c -> {
                            ProgressSavedData.get(c.getSource().getServer()).set(c.getSource().getServer(), StringArgumentType.getString(c, "flag"));
                            return 1;
                        })))
                        .then(Commands.literal("clear").then(Commands.argument("flag", StringArgumentType.greedyString()).executes(c -> {
                            ProgressSavedData.get(c.getSource().getServer()).clear(StringArgumentType.getString(c, "flag"));
                            return 1;
                        })))
                        .then(Commands.literal("list").executes(c -> {
                            ProgressSavedData data = ProgressSavedData.get(c.getSource().getServer());
                            ok(c, () -> Component.literal("flags: " + data.flags() + " choices: " + data.choices()));
                            return 1;
                        })))
                .then(Commands.literal("ending").executes(c -> {
                    String ending = Chapters.ending(c.getSource().getServer());
                    ok(c, () -> ending == null ? Component.translatable("story.rpgcore.not_yet") : Component.translatable(ending));
                    return 1;
                }))
                // ---- towns & quests ----
                .then(Commands.literal("town")
                        .then(Commands.literal("create").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(TOWNS).executes(RpgCommands::townCreate)))
                        .then(Commands.literal("inn").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(TOWNS).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            TownSavedData.get(c.getSource().getServer()).setInn(ResourceLocationArgument.getId(c, "id"), p.blockPosition().below());
                            return 1;
                        }))))
                .then(Commands.literal("npc").then(Commands.argument("role", StringArgumentType.word())
                        .then(Commands.argument("dialogue", ResourceLocationArgument.id()).executes(RpgCommands::npc))))
                .then(Commands.literal("quest")
                        .then(Commands.literal("start").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(QUESTS).executes(c -> {
                            boolean ok = Quests.start(c.getSource().getPlayerOrException(), ResourceLocationArgument.getId(c, "id"));
                            return ok ? 1 : 0;
                        })))
                        .then(Commands.literal("advance").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(QUESTS)
                                .executes(c -> {
                                    Quests.advance(c.getSource().getPlayerOrException(), ResourceLocationArgument.getId(c, "id"), null);
                                    return 1;
                                })
                                .then(Commands.argument("choice", StringArgumentType.word()).executes(c -> {
                                    Quests.advance(c.getSource().getPlayerOrException(), ResourceLocationArgument.getId(c, "id"), StringArgumentType.getString(c, "choice"));
                                    return 1;
                                }))))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            QuestLog.reset(p);
                            Quests.sync(p);
                            return 1;
                        })))
                // ---- home ----
                .then(Commands.literal("blueprint").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(BLUEPRINTS).executes(c -> {
                    Inv.give(c.getSource().getPlayerOrException(), BlueprintItem.of(RpgItems.BLUEPRINT.get(), ResourceLocationArgument.getId(c, "id")));
                    return 1;
                })))
                .then(Commands.literal("unlock").then(Commands.argument("civ", ResourceLocationArgument.id()).executes(c -> {
                    Unlocks.unlock(c.getSource().getPlayerOrException(), ResourceLocationArgument.getId(c, "civ"));
                    return 1;
                })))
                .then(Commands.literal("scroll").then(Commands.argument("civ", ResourceLocationArgument.id()).executes(c -> {
                    Inv.give(c.getSource().getPlayerOrException(), CivilizationScrollItem.of(RpgItems.CIVILIZATION_SCROLL.get(), ResourceLocationArgument.getId(c, "civ")));
                    return 1;
                })))
                // ---- self test ----
                .then(Commands.literal("selftest").executes(c -> {
                    List<String> lines = SelfTest.run(c.getSource().getLevel());
                    for (String l : lines) ok(c, () -> Component.literal(l));
                    return lines.size();
                }).then(Commands.literal("castle").executes(c -> {
                    // builds a castle 64 blocks east of the caller and writes castle_top.png / castle_side.png
                    ServerLevel level = c.getSource().getLevel();
                    BlockPos at = BlockPos.containing(c.getSource().getPosition()).offset(64, 0, 0);
                    CastleCheck.Result r = CastleCheck.placeAndScan(level, at, new java.io.File(level.getServer().getServerDirectory(), "rpgcore_selftest"));
                    if (r == null || r.box() == null) {
                        c.getSource().sendFailure(Component.literal("castle did not generate"));
                        return 0;
                    }
                    ok(c, () -> Component.literal("castle: " + r.pieces() + " pieces at " + r.box() + " " + r.counts()));
                    return r.pieces();
                }))));
    }

    private static int unique(CommandContext<CommandSourceStack> c, int tierOrLocal) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Uniques.UniqueDef def = Uniques.get(ResourceLocationArgument.getId(c, "id"));
        if (def == null) {
            c.getSource().sendFailure(Component.literal("unknown unique (or its base item's mod is missing)"));
            return 0;
        }
        Inv.give(p, Uniques.create(def, tierOrLocal < 0 ? localTier(p) : tierOrLocal, p.getRandom()));
        return 1;
    }

    private static int loot(CommandContext<CommandSourceStack> c, int tierOrLocal) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int tier = tierOrLocal < 0 ? localTier(p) : tierOrLocal;
        List<ItemStack> items = LootSources.roll(ResourceLocationArgument.getId(c, "source"), p, tier, p.getRandom(), null);
        for (ItemStack s : items) Inv.give(p, s);
        ok(c, () -> Component.literal(items.size() + " item(s) at tier " + tier));
        return items.size();
    }

    private static int gear(CommandContext<CommandSourceStack> c, int tierOrLocal) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Rarity rarity = Rarity.byId(StringArgumentType.getString(c, "rarity"));
        if (rarity == null || rarity.isUnique()) {
            c.getSource().sendFailure(Component.literal("rarity: common | uncommon | rare (use /rpgcore unique for legendaries)"));
            return 0;
        }
        ItemStack s = Generator.gear(rarity, tierOrLocal < 0 ? localTier(p) : LootMath.clampTier(tierOrLocal), null, p.getRandom());
        if (s.isEmpty()) {
            c.getSource().sendFailure(Component.literal("no gear base fits"));
            return 0;
        }
        Inv.give(p, s);
        return 1;
    }

    private static int danger(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int tier = DangerMap.get(level, pos);
        int base = DangerMap.base(level, pos);
        Integer structure = DangerMap.structureTier(level, pos);
        int event = WorldEvents.dangerModifier(level, pos);
        ok(c, () -> Component.literal("danger " + tier + " (rings+biome " + base + ", structure " + structure + ", event +" + event + ")"));
        return tier;
    }

    private static int elite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Mob> mobs = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), m -> !Elites.isElite(m));
        if (mobs.isEmpty()) return 0;
        Elites.makeElite(mobs.get(0), 3, p.getRandom());
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BossDef def = BossDefs.get(ResourceLocationArgument.getId(c, "id"));
        RpgBoss boss = def == null ? null : RpgEntities.BOSS.get().create(p.level());
        if (boss == null) {
            c.getSource().sendFailure(Component.literal("unknown boss"));
            return 0;
        }
        boss.moveTo(p.getX() + 4, p.getY(), p.getZ(), 0F, 0F);
        boss.setBoss(def, null);
        p.level().addFreshEntity(boss);
        return 1;
    }

    private static int rift(CommandContext<CommandSourceStack> c, int tierOrLocal) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RiftInstance inst = RiftManager.open(List.of(p), tierOrLocal < 0 ? localTier(p) : tierOrLocal);
        return inst == null ? 0 : 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        EventMachine m = WorldEvents.machine();
        if (m == null) return 0;
        for (EventMachine.Instance i : m.active()) ok(c, () -> Component.literal(i.defId + ": " + i.state + " (day " + i.daysInState + ")"));
        ok(c, () -> Component.literal("queued " + m.queue() + " finished " + m.finished()));
        return m.active().size();
    }

    private static int townCreate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(c, "id");
        TownDefs.TownDef def = TownDefs.get(id);
        int radius = def == null ? 64 : def.radius();
        TownSavedData data = TownSavedData.get(c.getSource().getServer());
        TownSavedData.Town town = data.create(id, p.level().dimension(), p.blockPosition(), radius);
        int npcs = def == null ? 0 : TownSavedData.spawnTemplateNpcs(p.serverLevel(), town, def);
        ok(c, () -> Component.literal("town " + id + " created, " + npcs + " NPC(s)"));
        return 1;
    }

    private static int npc(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        NpcEntity npc = RpgEntities.NPC.get().create(p.level());
        if (npc == null) return 0;
        String role = StringArgumentType.getString(c, "role");
        TownSavedData.Town town = TownSavedData.get(c.getSource().getServer()).townAt(p.level().dimension(), p.blockPosition());
        npc.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180F, 0F);
        npc.configure(role, "npc.rpgcore." + role, ResourceLocationArgument.getId(c, "dialogue"), town == null ? null : town.id);
        p.level().addFreshEntity(npc);
        return 1;
    }
}
