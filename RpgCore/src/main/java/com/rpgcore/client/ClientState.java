package com.rpgcore.client;

import com.rpgcore.net.RpgPackets;

/** Client-side copies of server state for the HUD and screens. */
public final class ClientState {
    private ClientState() {}

    public static int dashCharges = -1;
    public static int dashMax;
    public static int dashTicksPerCharge = 1;
    public static int dashElapsed;

    public static RpgPackets.NavTarget nav;
    public static RpgPackets.Journal journal;

    public static void reset() {
        dashCharges = -1;
        dashMax = 0;
        nav = null;
        journal = null;
    }
}
