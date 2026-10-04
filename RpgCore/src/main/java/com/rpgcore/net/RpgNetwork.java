package com.rpgcore.net;

import com.rpgcore.RpgCore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** SimpleChannel for all rpgcore packets (§3 net/). */
public final class RpgNetwork {
    private RpgNetwork() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(RpgCore.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(RpgPackets.Dash.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RpgPackets.Dash::encode).decoder(RpgPackets.Dash::decode).consumerMainThread(RpgPackets.Dash::handle).add();
        CHANNEL.messageBuilder(RpgPackets.DashSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RpgPackets.DashSync::encode).decoder(RpgPackets.DashSync::decode).consumerMainThread(RpgPackets.DashSync::handle).add();
        CHANNEL.messageBuilder(RpgPackets.ForgeAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RpgPackets.ForgeAction::encode).decoder(RpgPackets.ForgeAction::decode).consumerMainThread(RpgPackets.ForgeAction::handle).add();
        CHANNEL.messageBuilder(RpgPackets.Dialogue.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RpgPackets.Dialogue::encode).decoder(RpgPackets.Dialogue::decode).consumerMainThread(RpgPackets.Dialogue::handle).add();
        CHANNEL.messageBuilder(RpgPackets.DialogueChoice.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RpgPackets.DialogueChoice::encode).decoder(RpgPackets.DialogueChoice::decode).consumerMainThread(RpgPackets.DialogueChoice::handle).add();
        CHANNEL.messageBuilder(RpgPackets.JournalRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RpgPackets.JournalRequest::encode).decoder(RpgPackets.JournalRequest::decode).consumerMainThread(RpgPackets.JournalRequest::handle).add();
        CHANNEL.messageBuilder(RpgPackets.Journal.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RpgPackets.Journal::encode).decoder(RpgPackets.Journal::decode).consumerMainThread(RpgPackets.Journal::handle).add();
        CHANNEL.messageBuilder(RpgPackets.NavToggle.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RpgPackets.NavToggle::encode).decoder(RpgPackets.NavToggle::decode).consumerMainThread(RpgPackets.NavToggle::handle).add();
        CHANNEL.messageBuilder(RpgPackets.NavTarget.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RpgPackets.NavTarget::encode).decoder(RpgPackets.NavTarget::decode).consumerMainThread(RpgPackets.NavTarget::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
