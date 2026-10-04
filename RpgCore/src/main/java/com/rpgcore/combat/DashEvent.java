package com.rpgcore.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Posted on the Forge bus after a successful dash (trait trigger "dash"). */
public class DashEvent extends PlayerEvent {
    public DashEvent(ServerPlayer player) {
        super(player);
    }
}
