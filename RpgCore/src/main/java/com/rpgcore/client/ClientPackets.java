package com.rpgcore.client;

import com.rpgcore.client.screen.DialogueScreen;
import com.rpgcore.client.screen.JournalScreen;
import com.rpgcore.net.RpgPackets;
import net.minecraft.client.Minecraft;

/** Client handlers for S2C packets (only loaded on the client through DistExecutor). */
public final class ClientPackets {
    private ClientPackets() {}

    public static void dashSync(RpgPackets.DashSync msg) {
        ClientState.dashCharges = msg.charges();
        ClientState.dashMax = msg.max();
        ClientState.dashTicksPerCharge = Math.max(1, msg.ticksPerCharge());
        ClientState.dashElapsed = msg.elapsed();
    }

    public static void dialogue(RpgPackets.Dialogue msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg.lines().isEmpty()) {
            if (mc.screen instanceof DialogueScreen d) d.closeSilently();
            return;
        }
        mc.setScreen(new DialogueScreen(msg));
    }

    public static void journal(RpgPackets.Journal msg) {
        ClientState.journal = msg;
        Minecraft mc = Minecraft.getInstance();
        if (msg.open()) mc.setScreen(new JournalScreen());
        else if (mc.screen instanceof JournalScreen j) j.refresh();
    }

    public static void navTarget(RpgPackets.NavTarget msg) {
        ClientState.nav = msg.has() ? msg : null;
    }
}
