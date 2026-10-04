package com.rpgcore.compat;

import com.rpgcore.trait.TraitCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Curios: equipped curio stacks count for traits (§5.3), and CurioChangeEvent refreshes the cache. */
public final class CuriosCompat {
    private CuriosCompat() {}

    private static Class<?> api;

    public static void setup() {
        api = Ref.type("top.theillusivec4.curios.api.CuriosApi");
        Ref.listen("top.theillusivec4.curios.api.event.CurioChangeEvent", EventPriority.NORMAL, e -> {
            if (Ref.call(e, "getEntity") instanceof ServerPlayer p) TraitCache.markDirty(p);
        });
    }

    /** ("curios/&lt;i&gt;", stack) for every equipped curio; empty without Curios. */
    public static List<Map.Entry<String, ItemStack>> equipped(ServerPlayer player) {
        List<Map.Entry<String, ItemStack>> out = new ArrayList<>();
        if (!Compat.curios || api == null) return out;
        Object lazy = Ref.callStatic(api, "getCuriosInventory", player);
        Object handler = Ref.call(lazy, "orElse", (Object) null);
        Object equipped = Ref.call(handler, "getEquippedCurios");
        if (!(equipped instanceof IItemHandler items)) return out;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) out.add(Map.entry("curios/" + i, stack));
        }
        return out;
    }
}
