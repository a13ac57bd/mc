package com.rpgcore.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class LogicTests {

    // ---------- traits (#3 #20) ----------

    private static TraitRules.RuleView rule(Trigger t, List<String> conds, List<String> effects) {
        return new TraitRules.RuleView(t, conds, effects);
    }

    @Test
    void gunTraitRejectsSpellTrigger() {
        var errors = TraitRules.validate(School.GUN, List.of(rule(Trigger.SPELL_HIT, List.of(), List.of("damage_mult"))));
        assertFalse(errors.isEmpty());
    }

    @Test
    void meleeAndBowShareTriggers() {
        assertTrue(TraitRules.validate(School.MELEE, List.of(rule(Trigger.BOW_HIT, List.of(), List.of("damage_mult")))).isEmpty());
        assertTrue(TraitRules.validate(School.BOW, List.of(rule(Trigger.MELEE_DEALT, List.of(), List.of("ignite")))).isEmpty());
    }

    @Test
    void anyCannotMixGunAndSpell() {
        var errors = TraitRules.validate(School.ANY, List.of(
                rule(Trigger.GUN_HIT, List.of(), List.of("damage_mult")),
                rule(Trigger.SPELL_HIT, List.of(), List.of("damage_mult"))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("mix")));
    }

    @Test
    void anyHitIsStateless() {
        assertFalse(TraitRules.validate(School.ANY, List.of(rule(Trigger.ANY_HIT, List.of(), List.of("counter_add")))).isEmpty());
        assertFalse(TraitRules.validate(School.ANY, List.of(rule(Trigger.ANY_HIT, List.of("flag"), List.of("damage_mult")))).isEmpty());
        assertTrue(TraitRules.validate(School.ANY, List.of(rule(Trigger.ANY_HIT, List.of("chance"), List.of("damage_mult")))).isEmpty());
    }

    @Test
    void passiveOnlyAttributes() {
        assertTrue(TraitRules.validate(School.ANY, List.of(rule(Trigger.PASSIVE, List.of(), List.of("attribute")))).isEmpty());
        assertFalse(TraitRules.validate(School.ANY, List.of(rule(Trigger.PASSIVE, List.of(), List.of("ignite")))).isEmpty());
        assertFalse(TraitRules.validate(School.GUN, List.of(rule(Trigger.GUN_HIT, List.of(), List.of("attribute")))).isEmpty());
    }

    @Test
    void preDamageEffectsOnlyOnHit() {
        assertFalse(TraitRules.validate(School.GUN, List.of(rule(Trigger.GUN_DEALT, List.of(), List.of("damage_mult")))).isEmpty());
        assertTrue(TraitRules.validate(School.GUN, List.of(rule(Trigger.GUN_DEALT, List.of(), List.of("heal_fraction")))).isEmpty());
    }

    // ---------- loot (#45 #48) ----------

    @Test
    void tierMultiplier() {
        assertEquals(1.0, LootMath.tierMultiplier(1), 1e-9);
        assertEquals(1.6, LootMath.tierMultiplier(5), 1e-9);
        assertEquals(1.6, LootMath.tierMultiplier(9), 1e-9);
    }

    @Test
    void pityGrows() {
        assertEquals(0.01, LootMath.pityChance(0.01, 0), 1e-12);
        assertEquals(0.02, LootMath.pityChance(0.01, 10), 1e-12);
        assertEquals(1.0, LootMath.pityChance(0.5, 100), 1e-12);
    }

    @Test
    void lootDistributionTenThousandKills() {
        RandomGenerator rng = new SplittableRandom(42);
        var spec = new LootRoller.Spec(0.10, new double[]{60, 30, 10}, 0.002, 1);
        int gear = 0, legendaries = 0, misses = 0;
        Map<Rarity, Integer> counts = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            var r = LootRoller.roll(spec, misses, false, rng);
            misses = r.misses();
            gear += r.equipment().size();
            for (Rarity rar : r.equipment()) counts.merge(rar, 1, Integer::sum);
            if (r.legendary()) legendaries++;
        }
        assertEquals(1000, gear, 120);
        double commonShare = counts.getOrDefault(Rarity.COMMON, 0) / (double) gear;
        assertEquals(0.60, commonShare, 0.06);
        // pity: effective rate is clearly above the base 0.2%
        assertTrue(legendaries > 20, "legendaries=" + legendaries);
        assertTrue(legendaries < 200, "legendaries=" + legendaries);
    }

    @Test
    void pityResetsOnHit() {
        RandomGenerator alwaysHit = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        var spec = new LootRoller.Spec(0, new double[]{1, 0, 0}, 1.0, 1);
        var r = LootRoller.roll(spec, 7, false, alwaysHit);
        assertTrue(r.legendary());
        assertEquals(0, r.misses());
        var forced = LootRoller.roll(new LootRoller.Spec(0, new double[]{1, 0, 0}, 0.0, 1), 3, true, alwaysHit);
        assertTrue(forced.legendary());
    }

    @Test
    void forgeCosts() {
        assertEquals(5, ForgeCosts.upgradeCost(1));
        assertEquals(-1, ForgeCosts.upgradeCost(5));
        assertEquals(2, ForgeCosts.rerollCost(0));
        assertEquals(12, ForgeCosts.rerollCost(50));
        assertFalse(ForgeCosts.canReroll(Rarity.UNCOMMON));
        assertTrue(ForgeCosts.canReroll(Rarity.RARE));
        assertTrue(ForgeCosts.autoSalvage(1, Rarity.COMMON));
        assertFalse(ForgeCosts.autoSalvage(1, Rarity.UNCOMMON));
        assertTrue(ForgeCosts.autoSalvage(2, Rarity.UNCOMMON));
        assertFalse(ForgeCosts.autoSalvage(2, Rarity.RARE));
        assertFalse(ForgeCosts.autoSalvage(3, Rarity.LEGENDARY));
        assertFalse(ForgeCosts.autoSalvage(0, Rarity.COMMON));
    }

    @Test
    void rarityMinorCounts() {
        assertEquals(0, Rarity.COMMON.minorCount(0.1));
        assertEquals(1, Rarity.UNCOMMON.minorCount(0.9));
        assertEquals(2, Rarity.RARE.minorCount(0.3));
        assertEquals(0, Rarity.LEGENDARY.minorCount(0.2));
        assertEquals(1, Rarity.LEGENDARY.minorCount(0.7));
    }

    // ---------- danger ----------

    @Test
    void dangerRings() {
        int[] rings = DangerMath.DEFAULT_RINGS;
        assertEquals(1, DangerMath.ringTier(0, rings));
        assertEquals(2, DangerMath.ringTier(1000, rings));
        assertEquals(3, DangerMath.ringTier(4999, rings));
        assertEquals(5, DangerMath.ringTier(100000, rings));
        assertEquals(1.0, DangerMath.damageMultiplier(1), 1e-9);
        assertEquals(2.0, DangerMath.damageMultiplier(5), 1e-9);
        assertEquals(1.4, DangerMath.healthBonus(5), 1e-9);
        assertEquals(3, DangerMath.eliteAffixes(5));
    }

    // ---------- combat ----------

    @Test
    void dashAlwaysTravelsDistance() {
        for (float friction : new float[]{0.6f, 0.8f, 0.98f}) {
            double drag = CombatMath.horizontalDrag(friction, true);
            double v0 = CombatMath.dashSpeed(4.0, drag);
            // simulate vanilla: move by v, then v *= drag
            double travelled = 0, v = v0;
            for (int t = 0; t < 2000; t++) {
                travelled += v;
                v *= drag;
            }
            assertEquals(4.0, travelled, 1e-6, "friction " + friction);
        }
    }

    @Test
    void frontCone() {
        assertTrue(CombatMath.inFrontCone(0, 1, 0.1, 1, 60));
        assertFalse(CombatMath.inFrontCone(0, 1, 1, 0.2, 60));
        assertFalse(CombatMath.inFrontCone(0, 1, 0, -1, 60));
    }

    @Test
    void xpTotals() {
        assertEquals(0, XpMath.total(0, 0f));
        assertEquals(7, XpMath.total(1, 0f));
        assertEquals(315, XpMath.total(15, 0f));
        assertEquals(1395, XpMath.total(30, 0f));
        assertEquals(1395 + 56, XpMath.total(30, 0.5f));
    }

    // ---------- rift ----------

    @Test
    void riftNumbers() {
        assertEquals(1.0, RiftMath.strength(1, 0.15), 1e-9);
        assertEquals(1.6, RiftMath.strength(5, 0.15), 1e-9);
        assertEquals(1.8, RiftMath.rewardMultiplier(5, 0.2), 1e-9);
        assertEquals(8, RiftMath.mobCount(5, 4, 1));
        assertEquals(0.0, RiftMath.bossChance(4, 5, 0.2));
        assertEquals(0.2, RiftMath.bossChance(5, 5, 0.2));
        assertEquals(2, RiftMath.rewardRolls(1.8, 0.5));
        assertEquals(1, RiftMath.rewardRolls(1.8, 0.9));
    }

    @Test
    void riftHalving() {
        assertEquals(List.of(32, 1, 0, 1, 0, 2), RiftMath.halve(List.of(64, 1, 1, 1, 0, 5)));
    }

    // ---------- events (#14) ----------

    private static EventMachine.Def siege(String id) {
        Map<String, EventMachine.State> states = new HashMap<>();
        states.put("brewing", new EventMachine.State("brewing", 2, "attack", null, null));
        states.put("attack", new EventMachine.State("attack", 3, "fallen", "held", null));
        states.put("held", new EventMachine.State("held", 0, null, null, "held"));
        states.put("fallen", new EventMachine.State("fallen", 0, null, "reclaimed", null));
        states.put("reclaimed", new EventMachine.State("reclaimed", 0, null, null, "reclaimed"));
        return new EventMachine.Def(id, "brewing", states);
    }

    @Test
    void eventTimelineAndQueue() {
        Map<String, EventMachine.Def> defs = new HashMap<>();
        for (String id : List.of("a", "b", "c")) defs.put(id, siege(id));
        for (var d : defs.values()) assertTrue(EventMachine.validate(d).isEmpty(), EventMachine.validate(d).toString());
        EventMachine m = new EventMachine(defs, 2);
        List<EventMachine.Transition> out = new ArrayList<>();
        assertEquals(EventMachine.StartResult.STARTED, m.trigger("a", out));
        assertEquals(EventMachine.StartResult.STARTED, m.trigger("b", out));
        assertEquals(EventMachine.StartResult.QUEUED, m.trigger("c", out));
        assertEquals(EventMachine.StartResult.ALREADY_KNOWN, m.trigger("a", out));

        m.dailyTick();
        m.dailyTick();
        assertEquals("attack", m.get("a").state);
        // defend a -> held, which frees a slot for c
        var t = m.goalMet("a");
        assertTrue(t.stream().anyMatch(x -> "held".equals(x.outcome())));
        assertEquals("held", m.finished().get("a"));
        assertNotNull(m.get("c"));
        // b is ignored for 3 days -> fallen, then reclaimed by the players
        for (int i = 0; i < 3; i++) m.dailyTick();
        assertEquals("fallen", m.get("b").state);
        for (int i = 0; i < 10; i++) m.dailyTick();
        assertEquals("fallen", m.get("b").state, "fallen waits for the goal");
        m.goalMet("b");
        assertEquals("reclaimed", m.finished().get("b"));
    }

    // ---------- quests ----------

    @Test
    void questFlowWithChoices() {
        QuestFlow flow = new QuestFlow(List.of(
                new QuestFlow.Step("talk", null, Map.of()),
                new QuestFlow.Step("find", null, Map.of()),
                new QuestFlow.Step("choose", null, Map.of("return", "thanks", "keep", "end")),
                new QuestFlow.Step("thanks", "end", Map.of())));
        assertTrue(flow.validate().isEmpty());
        assertEquals("find", flow.next("talk", null));
        assertEquals("choose", flow.next("find", null));
        assertEquals("choose", flow.next("choose", null));
        assertEquals("thanks", flow.next("choose", "return"));
        assertNull(flow.next("choose", "keep"));
        assertNull(flow.next("thanks", null));
    }

    @Test
    void endings() {
        var rules = List.of(
                new EndingTable.Rule(Map.of("ch1", "spare", "ch2", "burn"), "dark"),
                new EndingTable.Rule(Map.of("ch1", "spare"), "merciful"),
                new EndingTable.Rule(Map.of("ch3", "*"), "wanderer"));
        assertEquals("dark", EndingTable.resolve(rules, "neutral", Map.of("ch1", "spare", "ch2", "burn")));
        assertEquals("merciful", EndingTable.resolve(rules, "neutral", Map.of("ch1", "spare", "ch2", "save")));
        assertEquals("wanderer", EndingTable.resolve(rules, "neutral", Map.of("ch1", "kill", "ch3", "x")));
        assertEquals("neutral", EndingTable.resolve(rules, "neutral", Map.of()));
        assertFalse(EndingTable.endgameReady(2));
        assertTrue(EndingTable.endgameReady(3));
    }

    // ---------- flood fill ----------

    @Test
    void floodFillIsBounded() {
        // a 20x20 grid packed as x*100+y
        var all = FloodFill.<Integer>fill(0,
                p -> List.of(p + 1, p - 1, p + 100, p - 100),
                p -> p >= 0 && p % 100 < 20 && p / 100 < 20 && p % 100 >= 0,
                64);
        assertEquals(64, all.size());
        var none = FloodFill.<Integer>fill(-5, p -> List.of(), p -> p >= 0, 64);
        assertTrue(none.isEmpty());
    }
}
