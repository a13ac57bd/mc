package com.rpgcore.compat;

import com.rpgcore.RpgCore;
import net.minecraftforge.fml.ModList;

/**
 * Optional mod integration (§3 compat/). Every integration is bound by reflection at runtime, so rpgcore compiles
 * and runs without TACZ, Iron's Spells or Curios. The class and method names come from the M0 API notes; if a mod
 * changes them the binding logs a warning and that integration turns off instead of crashing.
 */
public final class Compat {
    private Compat() {}

    public static boolean tacz;
    public static boolean irons;
    public static boolean curios;

    public static void init() {
        ModList mods = ModList.get();
        tacz = mods.isLoaded("tacz");
        irons = mods.isLoaded("irons_spellbooks");
        curios = mods.isLoaded("curios");
        RpgCore.LOG.info("rpgcore compat: tacz={} irons={} curios={}", tacz, irons, curios);
    }

    /** Called from common setup. */
    public static void setup() {
        if (tacz) TaczCompat.setup();
        if (irons) IronsCompat.setup();
        if (curios) CuriosCompat.setup();
    }
}
