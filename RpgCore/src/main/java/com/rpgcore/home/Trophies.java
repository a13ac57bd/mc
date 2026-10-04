package com.rpgcore.home;

import com.rpgcore.boss.BossDef;
import com.rpgcore.boss.BossDefs;
import com.rpgcore.registry.RpgItems;
import com.rpgcore.util.Inv;
import com.rpgcore.util.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** One trophy per boss per player, given on the first kill (§16). */
public final class Trophies {
    private Trophies() {}

    public static Component bossName(ResourceLocation boss) {
        BossDef def = BossDefs.get(boss);
        if (def != null) return Component.translatable(def.name());
        if (ForgeRegistries.ENTITY_TYPES.containsKey(boss)) return ForgeRegistries.ENTITY_TYPES.getValue(boss).getDescription();
        return Component.literal(boss.toString());
    }

    public static ItemStack create(ResourceLocation boss) {
        ItemStack stack = new ItemStack(RpgItems.TROPHY.get());
        CompoundTag be = new CompoundTag();
        be.putString("boss", boss.toString());
        stack.addTagElement("BlockEntityTag", be);
        return stack;
    }

    /** Gives the trophy if this player has none for this boss yet. */
    public static boolean grant(ServerPlayer p, ResourceLocation boss) {
        CompoundTag root = PlayerData.root(p);
        ListTag got = root.getList("trophies", Tag.TAG_STRING);
        for (int i = 0; i < got.size(); i++) if (got.getString(i).equals(boss.toString())) return false;
        got.add(StringTag.valueOf(boss.toString()));
        root.put("trophies", got);
        Inv.give(p, create(boss));
        p.displayClientMessage(Component.translatable("block.rpgcore.trophy.got", bossName(boss)), false);
        return true;
    }
}
