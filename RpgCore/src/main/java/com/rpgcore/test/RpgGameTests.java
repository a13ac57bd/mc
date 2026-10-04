package com.rpgcore.test;

import com.google.gson.JsonParser;
import com.rpgcore.RpgCore;
import com.rpgcore.ai.KiteGoal;
import com.rpgcore.boss.BossDef;
import com.rpgcore.boss.BossDefs;
import com.rpgcore.boss.RpgBoss;
import com.rpgcore.combat.Scaling;
import com.rpgcore.compat.Compat;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.danger.Elites;
import com.rpgcore.dungeon.ChandelierBlock;
import com.rpgcore.dungeon.FragileChainBlock;
import com.rpgcore.dungeon.Mechanisms;
import com.rpgcore.dungeon.OilBlock;
import com.rpgcore.dungeon.SecretWallBlock;
import com.rpgcore.dungeon.ShortcutDoorBlock;
import com.rpgcore.ecology.Factions;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.loot.LootSources;
import com.rpgcore.registry.RpgBlocks;
import com.rpgcore.registry.RpgEffects;
import com.rpgcore.registry.RpgEntities;
import com.rpgcore.registry.RpgItems;
import com.rpgcore.trait.TraitEngine;
import com.rpgcore.trait.TraitRegistry;
import com.rpgcore.util.Attr;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.util.List;

/**
 * GameTests (§18). Run with gradlew runGameTestServer (vanilla + rpgcore only), or in the full pack through /test.
 * Tests needing TACZ or Iron's pass as "skipped" when those mods are missing.
 */
@GameTestHolder(RpgCore.MODID)
@PrefixGameTestTemplate(false)
public final class RpgGameTests {
    private RpgGameTests() {}

    /** 8x8x8 with a stone floor. Relative y=0 is the structure block row, so the floor is y=1 and tests start at y=2. */
    private static final String EMPTY = "empty";

    // ---------- combat (#16 #17) ----------

    @GameTest(template = EMPTY)
    public static void healthIsScaledX5(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        h.assertTrue(Attr.has(z, Attributes.MAX_HEALTH, Scaling.HEALTH_ID), "x5 modifier missing");
        h.assertTrue(Math.abs(z.getMaxHealth() - 20F * CombatMath.SCALE) < 0.01F, "max health " + z.getMaxHealth());
        h.assertTrue(z.getHealth() >= z.getMaxHealth() - 0.01F, "not full: " + z.getHealth());
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void finalDamageIsScaledX5(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        float before = z.getHealth();
        // magic bypasses armor, so the only change is the x5 at LivingDamageEvent
        z.hurt(h.getLevel().damageSources().magic(), 4F);
        float lost = before - z.getHealth();
        h.assertTrue(Math.abs(lost - 4F * CombatMath.SCALE) < 0.01F, "lost " + lost);
        h.succeed();
    }

    // ---------- traits (#3) ----------

    @GameTest(template = EMPTY)
    public static void crossSchoolTraitIsRejected(GameTestHelper h) {
        String json = "{\"type\":\"unique\",\"school\":\"gun\",\"rules\":[{\"trigger\":\"spell_hit\",\"effects\":[{\"type\":\"damage_mult\",\"value\":0.5}]}]}";
        try {
            TraitRegistry.parse(RpgCore.id("test_bad"), JsonParser.parseString(json).getAsJsonObject());
            h.fail("gun trait with spell trigger was accepted");
        } catch (IllegalArgumentException expected) {
            h.succeed();
        }
    }

    @GameTest(template = EMPTY)
    public static void recursionStopsAtDepth2(GameTestHelper h) {
        int[] deepest = {0};
        TraitEngine.nested(() -> {
            deepest[0] = Math.max(deepest[0], TraitEngine.depth());
            TraitEngine.nested(() -> {
                deepest[0] = Math.max(deepest[0], TraitEngine.depth());
                TraitEngine.nested(() -> deepest[0] = Math.max(deepest[0], TraitEngine.depth()));
            });
        });
        h.assertTrue(deepest[0] == TraitEngine.MAX_DEPTH, "deepest " + deepest[0]);
        h.assertTrue(TraitEngine.depth() == 0, "depth leaked");
        h.succeed();
    }

    // ---------- loot / danger / elites ----------

    @GameTest(template = EMPTY)
    public static void chestSourceGivesMaterials(GameTestHelper h) {
        List<ItemStack> items = LootSources.roll(LootSources.CHEST, null, 3, h.getLevel().random, null);
        h.assertTrue(items.stream().anyMatch(s -> s.is(RpgItems.essence(3))), "no tier 3 material in " + items);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dangerIsInRange(GameTestHelper h) {
        int tier = DangerMap.get(h.getLevel(), h.absolutePos(BlockPos.ZERO));
        h.assertTrue(tier >= 1 && tier <= 5, "tier " + tier);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void eliteGetsAffixes(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        Elites.makeElite(z, 2, h.getLevel().random);
        h.assertTrue(Elites.isElite(z) && Elites.affixes(z).size() == 2, "affixes " + Elites.affixes(z));
        h.succeed();
    }

    // ---------- AI & ecology (§12) ----------

    @GameTest(template = EMPTY)
    public static void archerGetsKiteGoal(GameTestHelper h) {
        Skeleton s = h.spawn(EntityType.SKELETON, new BlockPos(2, 2, 2));
        boolean has = false;
        for (WrappedGoal g : s.goalSelector.getAvailableGoals()) has |= g.getGoal() instanceof KiteGoal;
        h.assertTrue(has, "skeleton has no KiteGoal (role tag missing?)");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void undeadAndBanditsAreHostile(GameTestHelper h) {
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Pillager p = h.spawn(EntityType.PILLAGER, new BlockPos(5, 2, 5));
        h.assertTrue(Factions.hostile(z, p), "zombie and pillager should be hostile");
        h.assertTrue(!Factions.hostile(z, h.spawn(EntityType.SKELETON, new BlockPos(3, 2, 3))), "undead should be allies");
        h.succeed();
    }

    // ---------- dungeon blocks & mechanisms (§9 §15) ----------

    @GameTest(template = EMPTY)
    public static void secretWallOpensConnectedWalls(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(3, y, 3), RpgBlocks.SECRET_WALL.get());
        int removed = SecretWallBlock.reveal(h.getLevel(), h.absolutePos(new BlockPos(3, 2, 3)));
        h.assertTrue(removed == 3, "removed " + removed);
        h.assertBlockNotPresent(RpgBlocks.SECRET_WALL.get(), new BlockPos(3, 4, 3));
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void shortcutDoorOpensColumn(GameTestHelper h) {
        h.setBlock(new BlockPos(3, 2, 3), RpgBlocks.SHORTCUT_DOOR.get());
        h.setBlock(new BlockPos(3, 3, 3), RpgBlocks.SHORTCUT_DOOR.get());
        ShortcutDoorBlock.open(h.getLevel(), h.absolutePos(new BlockPos(3, 2, 3)));
        h.assertTrue(h.getBlockState(new BlockPos(3, 3, 3)).getValue(ShortcutDoorBlock.OPEN), "upper door closed");
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void powderKegsChainAndHurtMobs(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 2, 2), RpgBlocks.POWDER_KEG.get());
        h.setBlock(new BlockPos(4, 2, 2), RpgBlocks.POWDER_KEG.get());
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        float before = z.getHealth();
        Mechanisms.explodeKeg(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), null);
        h.succeedWhen(() -> {
            h.assertBlockNotPresent(RpgBlocks.POWDER_KEG.get(), new BlockPos(4, 2, 2));
            h.assertTrue(z.getHealth() < before, "zombie not hurt");
        });
    }

    @GameTest(template = EMPTY)
    public static void oilPoolIgnites(GameTestHelper h) {
        for (int x = 1; x <= 4; x++) h.setBlock(new BlockPos(x, 2, 3), RpgBlocks.OIL.get());
        int burnt = OilBlock.ignite(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 3)));
        h.assertTrue(burnt == 4, "burnt " + burnt);
        h.assertBlockPresent(Blocks.FIRE, new BlockPos(4, 2, 3));
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void chandelierFallsAndStuns(GameTestHelper h) {
        h.setBlock(new BlockPos(3, 7, 3), Blocks.STONE);
        h.setBlock(new BlockPos(3, 6, 3), RpgBlocks.FRAGILE_CHAIN.get());
        h.setBlock(new BlockPos(3, 5, 3), RpgBlocks.CHANDELIER.get());
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        float before = z.getHealth();
        FragileChainBlock.snap(h.getLevel(), h.absolutePos(new BlockPos(3, 6, 3)));
        h.succeedWhen(() -> {
            h.assertTrue(z.getHealth() < before, "not hurt (" + ChandelierBlock.DAMAGE_PER_BLOCK + "/block)");
            h.assertTrue(z.hasEffect(RpgEffects.STUN.get()), "not stunned");
        });
    }

    // ---------- bosses (§14) ----------

    @GameTest(template = EMPTY)
    public static void bossEntersTransitionAtHalfHealth(GameTestHelper h) {
        BossDef def = BossDefs.get(ResourceLocation.fromNamespaceAndPath(RpgCore.MODID, "test_boss"));
        if (def == null) {
            h.fail("test_boss definition missing");
            return;
        }
        RpgBoss boss = RpgEntities.BOSS.get().create(h.getLevel());
        BlockPos p = h.absolutePos(new BlockPos(3, 2, 3));
        boss.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0F, 0F);
        boss.setBoss(def, null);
        h.getLevel().addFreshEntity(boss);
        boss.hurt(h.getLevel().damageSources().generic(), boss.getMaxHealth() * 0.6F / CombatMath.SCALE);
        h.assertTrue(boss.phase() == 2, "phase " + boss.phase());
        float hp = boss.getHealth();
        boss.hurt(h.getLevel().damageSources().generic(), 1F);
        h.assertTrue(boss.getHealth() == hp, "took damage during transition");
        boss.discard();
        h.succeed();
    }

    // ---------- self tests (§18) ----------

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void allEntitiesAreScaledX5(GameTestHelper h) {
        int[] checked = new int[1];
        List<String> bad = SelfTest.entityProblems(h.getLevel(), checked);
        h.assertTrue(checked[0] > 50, "only " + checked[0] + " entity types checked");
        h.assertTrue(bad.isEmpty(), "not scaled / not full: " + bad);
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void castleGenerates(GameTestHelper h) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO).offset(2048, 0, 2048);
        CastleCheck.Result r = CastleCheck.placeAndScan(h.getLevel(), origin, new File(h.getLevel().getServer().getServerDirectory(), "rpgcore_selftest"));
        h.assertTrue(r != null && r.pieces() >= 5, "castle pieces: " + (r == null ? "structure missing" : r.pieces()));
        h.assertTrue(r.count("minecraft:jigsaw") == 0, "leftover jigsaw blocks: " + r.count("minecraft:jigsaw"));
        h.assertTrue(r.count("rpgcore:oath_stone") == 1, "oath stones: " + r.counts());
        h.assertTrue(r.count("rpgcore:boss_altar") == 1, "boss altars: " + r.counts());
        RpgCore.LOG.info("castle self-test: {} pieces, box {}, blocks {}", r.pieces(), r.box(), r.counts());
        h.succeed();
    }

    /** Natural generation (terrain adaptation included). Slow, so not required. */
    @GameTest(template = EMPTY, timeoutTicks = 400, required = false)
    public static void naturalCastleGenerates(GameTestHelper h) {
        CastleCheck.Result r = CastleCheck.locateAndScan(h.getLevel(), h.absolutePos(BlockPos.ZERO), 100,
                new File(h.getLevel().getServer().getServerDirectory(), "rpgcore_selftest"));
        h.assertTrue(r != null, "no castle within 100 chunks");
        h.assertTrue(r.pieces() >= 5 && r.count("minecraft:jigsaw") == 0, "pieces " + r.pieces() + " blocks " + r.counts());
        RpgCore.LOG.info("natural castle: {} pieces, box {}, blocks {}", r.pieces(), r.box(), r.counts());
        h.succeed();
    }

    // ---------- compat (skipped without the mods) ----------

    @GameTest(template = EMPTY)
    public static void taczBindingPresent(GameTestHelper h) {
        if (!Compat.tacz) {
            RpgCore.LOG.info("gametest taczBindingPresent skipped: TACZ not installed");
            h.succeed();
            return;
        }
        h.assertTrue(com.rpgcore.compat.Ref.type("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre") != null, "TACZ event class missing");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ironsBindingPresent(GameTestHelper h) {
        if (!Compat.irons) {
            RpgCore.LOG.info("gametest ironsBindingPresent skipped: Iron's Spells not installed");
            h.succeed();
            return;
        }
        h.assertTrue(com.rpgcore.compat.Ref.type("io.redspace.ironsspellbooks.api.events.SpellDamageEvent") != null, "Iron's event class missing");
        h.succeed();
    }
}
