package com.rpgcore.compat;

import com.rpgcore.combat.DamageRules;
import com.rpgcore.logic.Trigger;
import com.rpgcore.trait.TraitContext;
import com.rpgcore.trait.TraitEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;
import java.util.List;

/**
 * Iron's Spells 'n Spellbooks: spell_cast / spell_hit / spell_dealt triggers and the extra_casts effect (§5.2, §5.3.5).
 * Note (§5.3.7): spell schools come from Iron's config and are only known after the first datapack sync.
 */
public final class IronsCompat {
    private IronsCompat() {}

    private static final String API = "io.redspace.ironsspellbooks.api.";

    private record PendingSpell(LivingEntity target, ResourceLocation spell, String school) {}

    private record ExtraCast(ServerPlayer player, String spellId, int level, float yawOffset) {}

    private static PendingSpell pending;
    private static final List<ExtraCast> QUEUE = new ArrayList<>();
    private static boolean replaying;

    public static void setup() {
        Ref.listen(API + "events.SpellOnCastEvent", EventPriority.NORMAL, IronsCompat::onCast);
        Ref.listen(API + "events.SpellDamageEvent", EventPriority.NORMAL, IronsCompat::onSpellDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, LivingDamageEvent.class, IronsCompat::onDealt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, IronsCompat::onServerTick);
    }

    private static String schoolOf(Object schoolType) {
        Object id = Ref.call(schoolType, "getId");
        return id == null ? null : id.toString();
    }

    private static void onCast(Object event) {
        if (!(Ref.call(event, "getEntity") instanceof ServerPlayer p) || replaying) return;
        if (!TraitEngine.has(p, Trigger.SPELL_CAST)) return;
        TraitContext ctx = new TraitContext(p, Trigger.SPELL_CAST);
        Object spellId = Ref.call(event, "getSpellId");
        ctx.spell = spellId == null ? null : ResourceLocation.tryParse(spellId.toString());
        ctx.spellSchool = schoolOf(Ref.call(event, "getSchoolType"));
        int level = Ref.asInt(Ref.call(event, "getSpellLevel"), 1);
        ctx.damage = level;
        TraitEngine.fire(ctx);
    }

    private static void onSpellDamage(Object event) {
        pending = null;
        Object source = Ref.call(event, "getSpellDamageSource");
        if (source == null || !(Ref.call(source, "getEntity") instanceof ServerPlayer p)) return;
        if (!(Ref.call(event, "getEntity") instanceof LivingEntity target)) return;
        Object spell = Ref.call(source, "spell");
        Object spellId = Ref.call(spell, "getSpellId");
        ResourceLocation id = spellId == null ? null : ResourceLocation.tryParse(spellId.toString());
        String school = schoolOf(Ref.call(spell, "getSchoolType"));
        pending = new PendingSpell(target, id, school);
        if (!TraitEngine.has(p, Trigger.SPELL_HIT)) return;
        TraitContext ctx = new TraitContext(p, Trigger.SPELL_HIT).target(target).damage(Ref.asFloat(Ref.call(event, "getAmount"), 0F));
        ctx.spell = id;
        ctx.spellSchool = school;
        TraitEngine.fire(ctx);
        if (ctx.bonus != 0) Ref.call(event, "setAmount", ctx.finalDamage());
    }

    private static void onDealt(LivingDamageEvent event) {
        PendingSpell pend = pending;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (DamageRules.classify(event.getSource()) != DamageRules.Kind.SPELL) return;
        pending = null;
        if (!TraitEngine.has(p, Trigger.SPELL_DEALT)) return;
        TraitContext ctx = new TraitContext(p, Trigger.SPELL_DEALT).target(event.getEntity()).source(event.getSource()).damage(event.getAmount());
        if (pend != null && pend.target() == event.getEntity()) {
            ctx.spell = pend.spell();
            ctx.spellSchool = pend.school();
        }
        TraitEngine.fire(ctx);
    }

    /**
     * extra_casts: next tick, cast the same spell {@code count} more times with the caster turned by +-spread degrees,
     * via AbstractSpell#onCast (no mana cost, no cooldown).
     */
    public static void extraCasts(TraitContext ctx, int count, double spread) {
        if (!Compat.irons || ctx.spell == null || ctx.extraCast || replaying) return;
        int level = Math.max(1, (int) ctx.damage);
        for (int i = 0; i < count; i++) {
            int side = (i % 2 == 0) ? 1 : -1;
            float offset = (float) (side * spread * (i / 2 + 1));
            QUEUE.add(new ExtraCast(ctx.player, ctx.spell.toString(), level, offset));
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || QUEUE.isEmpty()) return;
        List<ExtraCast> casts = new ArrayList<>(QUEUE);
        QUEUE.clear();
        Class<?> registry = Ref.type(API + "registry.SpellRegistry");
        Class<?> magicData = Ref.type(API + "magic.MagicData");
        Class<?> castSource = Ref.type(API + "spells.CastSource");
        if (registry == null || magicData == null || castSource == null) return;
        Object command = enumConstant(castSource, "COMMAND");
        replaying = true;
        try {
            for (ExtraCast c : casts) {
                ServerPlayer p = c.player();
                if (p.isRemoved() || p.isDeadOrDying()) continue;
                Object spell = Ref.callStatic(registry, "getSpell", c.spellId());
                Object data = Ref.callStatic(magicData, "getPlayerMagicData", p);
                if (spell == null || data == null) continue;
                float yaw = p.getYRot();
                float headYaw = p.getYHeadRot();
                p.setYRot(yaw + c.yawOffset());
                p.setYHeadRot(headYaw + c.yawOffset());
                try {
                    Ref.call(spell, "onCast", p.level(), c.level(), p, command, data);
                } finally {
                    p.setYRot(yaw);
                    p.setYHeadRot(headYaw);
                }
            }
        } finally {
            replaying = false;
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        Object[] constants = type.getEnumConstants();
        if (constants == null) return null;
        for (Object o : constants) if (((Enum<?>) o).name().equals(name)) return o;
        return constants.length > 0 ? constants[0] : null;
    }
}
