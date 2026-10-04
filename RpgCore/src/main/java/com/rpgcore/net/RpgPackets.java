package com.rpgcore.net;

import com.rpgcore.client.ClientPackets;
import com.rpgcore.forge.ForgeOps;
import com.rpgcore.quest.Dialogues;
import com.rpgcore.quest.Quests;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** All packets. Client-bound handlers go through DistExecutor so the server never loads client classes. */
public final class RpgPackets {
    private RpgPackets() {}

    private static void onClient(Runnable r) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> r);
    }

    /** C2S: dash key pressed with the current movement input. */
    public record Dash(float forward, float strafe) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(forward);
            buf.writeFloat(strafe);
        }

        public static Dash decode(FriendlyByteBuf buf) {
            return new Dash(buf.readFloat(), buf.readFloat());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) com.rpgcore.combat.Dash.tryDash(p, Math.max(-1f, Math.min(1f, forward)), Math.max(-1f, Math.min(1f, strafe)));
        }
    }

    /** S2C: dash charges for the HUD. */
    public record DashSync(int charges, int max, int ticksPerCharge, int elapsed) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(charges);
            buf.writeVarInt(max);
            buf.writeVarInt(ticksPerCharge);
            buf.writeVarInt(elapsed);
        }

        public static DashSync decode(FriendlyByteBuf buf) {
            return new DashSync(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            onClient(() -> ClientPackets.dashSync(this));
        }
    }

    /** C2S: forge table button (action ids in ForgeOps). */
    public record ForgeAction(int action, int arg) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(action);
            buf.writeVarInt(arg);
        }

        public static ForgeAction decode(FriendlyByteBuf buf) {
            return new ForgeAction(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ForgeOps.handle(p, action, arg);
        }
    }

    /** S2C: open/update the dialogue box. Texts are translation keys (lang files, §13). Empty lines close it. */
    public record Dialogue(int entityId, String speaker, List<String> lines, List<String> options) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUtf(speaker);
            buf.writeCollection(lines, FriendlyByteBuf::writeUtf);
            buf.writeCollection(options, FriendlyByteBuf::writeUtf);
        }

        public static Dialogue decode(FriendlyByteBuf buf) {
            return new Dialogue(buf.readVarInt(), buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf), buf.readList(FriendlyByteBuf::readUtf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            onClient(() -> ClientPackets.dialogue(this));
        }
    }

    /** C2S: dialogue option chosen (-1 = closed). */
    public record DialogueChoice(int entityId, int option) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(option);
        }

        public static DialogueChoice decode(FriendlyByteBuf buf) {
            return new DialogueChoice(buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Dialogues.choose(p, entityId, option);
        }
    }

    /** C2S: journal key pressed. */
    public record JournalRequest() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static JournalRequest decode(FriendlyByteBuf buf) {
            return new JournalRequest();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Quests.sendJournal(p, true);
        }
    }

    public record JournalEntry(String title, String step, boolean done) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(title);
            buf.writeUtf(step);
            buf.writeBoolean(done);
        }

        static JournalEntry read(FriendlyByteBuf buf) {
            return new JournalEntry(buf.readUtf(), buf.readUtf(), buf.readBoolean());
        }
    }

    /** S2C: quest journal contents. {@code open}: open the screen (otherwise only refresh if open). */
    public record Journal(List<JournalEntry> entries, List<String> clues, boolean nav, boolean open) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeCollection(entries, (b, e) -> e.write(b));
            buf.writeCollection(clues, FriendlyByteBuf::writeUtf);
            buf.writeBoolean(nav);
            buf.writeBoolean(open);
        }

        public static Journal decode(FriendlyByteBuf buf) {
            return new Journal(buf.readList(JournalEntry::read), buf.readList(FriendlyByteBuf::readUtf), buf.readBoolean(), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            onClient(() -> ClientPackets.journal(this));
        }
    }

    /** C2S: toggle the navigation hint (§13: can be turned off). */
    public record NavToggle() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static NavToggle decode(FriendlyByteBuf buf) {
            return new NavToggle();
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Quests.toggleNav(p);
        }
    }

    /** S2C: current navigation target for the HUD; {@code has == false} hides it. */
    public record NavTarget(boolean has, double x, double y, double z, String label) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(has);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUtf(label);
        }

        public static NavTarget decode(FriendlyByteBuf buf) {
            return new NavTarget(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            onClient(() -> ClientPackets.navTarget(this));
        }
    }
}
