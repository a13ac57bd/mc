package com.rpgcore.home;

import com.rpgcore.registry.RpgBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** State of a build table: blueprint, rotation, progress (§16). */
public class BuildTableBlockEntity extends BlockEntity {
    public enum Mode { IDLE, PREVIEW, BUILDING, PAUSED, DONE }

    private ResourceLocation blueprint;
    private Rotation rotation = Rotation.NONE;
    private Mode mode = Mode.IDLE;
    private int layer;
    private UUID owner;

    public BuildTableBlockEntity(BlockPos pos, BlockState state) {
        super(RpgBlockEntities.BUILD_TABLE.get(), pos, state);
    }

    public Mode mode() {
        return mode;
    }

    public int layer() {
        return layer;
    }

    /** First block of the footprint: two blocks in front of the table. */
    public BlockPos origin() {
        Direction facing = getBlockState().hasProperty(BuildTableBlock.FACING) ? getBlockState().getValue(BuildTableBlock.FACING) : Direction.NORTH;
        return worldPosition.relative(facing, 2);
    }

    public void interact(Player player, ItemStack held) {
        ResourceLocation id = BlueprintItem.blueprint(held);
        if (id != null) {
            Blueprints.BlueprintDef def = Blueprints.get(id);
            if (def == null) {
                say(player, "build.rpgcore.unknown");
                return;
            }
            if (!Unlocks.has(player, def.civilization())) {
                player.displayClientMessage(Component.translatable("build.rpgcore.locked", CivilizationScrollItem.civName(def.civilization())), true);
                return;
            }
            blueprint = id;
            mode = Mode.PREVIEW;
            layer = 0;
            owner = player.getUUID();
            setChanged();
            say(player, "build.rpgcore.preview");
            return;
        }
        if (blueprint == null) {
            say(player, "build.rpgcore.empty");
            return;
        }
        if (player.isShiftKeyDown() && mode == Mode.PREVIEW) {
            rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
            setChanged();
            say(player, "build.rpgcore.rotated");
            return;
        }
        switch (mode) {
            case PREVIEW, PAUSED -> {
                mode = Mode.BUILDING;
                owner = player.getUUID();
                say(player, "build.rpgcore.started");
            }
            case BUILDING -> {
                mode = Mode.PAUSED;
                say(player, "build.rpgcore.paused");
            }
            default -> say(player, "build.rpgcore." + mode.name().toLowerCase(Locale.ROOT));
        }
        setChanged();
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl) || blueprint == null) return;
        Blueprints.BlueprintDef def = Blueprints.get(blueprint);
        if (def == null) return;
        List<List<Blueprints.Placement>> layers = Blueprints.layers(sl, def);
        long t = sl.getGameTime();
        if (mode == Mode.PREVIEW && t % 10 == 0) outline(sl, layers);
        if (mode == Mode.BUILDING && t % 20 == 0) {
            if (layer >= layers.size()) {
                mode = Mode.DONE;
                notifyOwner(sl, Component.translatable("build.rpgcore.done"));
            } else if (placeLayer(sl, layers.get(layer))) {
                layer++;
            } else {
                mode = Mode.PAUSED;
            }
            setChanged();
        }
    }

    private BlockPos world(BlockPos rel) {
        return origin().offset(rel.rotate(rotation));
    }

    /** Places one layer, paying each block from adjacent containers. Returns false (and pauses) when short of material. */
    private boolean placeLayer(ServerLevel level, List<Blueprints.Placement> layer) {
        for (Blueprints.Placement p : layer) {
            BlockPos pos = world(p.pos());
            BlockState state = p.state().rotate(level, pos, rotation);
            if (level.getBlockState(pos).equals(state)) continue;
            Item item = state.getBlock().asItem();
            if (item != Items.AIR && !takeFromNeighbours(level, item)) {
                notifyOwner(level, Component.translatable("build.rpgcore.missing", new ItemStack(item).getHoverName()));
                return false;
            }
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        return true;
    }

    private boolean takeFromNeighbours(ServerLevel level, Item item) {
        for (Direction d : Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof Container c)) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.is(item)) {
                    s.shrink(1);
                    c.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    private void outline(ServerLevel level, List<List<Blueprints.Placement>> layers) {
        BlockPos size = Blueprints.size(layers);
        BlockPos a = world(BlockPos.ZERO);
        BlockPos b = world(new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX()) + 1;
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY()) + 1;
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ()) + 1;
        for (int x = x0; x <= x1; x++) {
            edge(level, x, y0, z0);
            edge(level, x, y0, z1);
            edge(level, x, y1, z0);
            edge(level, x, y1, z1);
        }
        for (int z = z0; z <= z1; z++) {
            edge(level, x0, y0, z);
            edge(level, x1, y0, z);
            edge(level, x0, y1, z);
            edge(level, x1, y1, z);
        }
        for (int y = y0; y <= y1; y++) {
            edge(level, x0, y, z0);
            edge(level, x1, y, z0);
            edge(level, x0, y, z1);
            edge(level, x1, y, z1);
        }
        // mark the front corner so the direction is visible
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
    }

    private static void edge(ServerLevel level, int x, int y, int z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
    }

    private void notifyOwner(ServerLevel level, Component msg) {
        if (owner == null) return;
        Player p = level.getPlayerByUUID(owner);
        if (p != null) p.displayClientMessage(msg, true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        blueprint = tag.contains("blueprint") ? ResourceLocation.tryParse(tag.getString("blueprint")) : null;
        try {
            rotation = Rotation.valueOf(tag.getString("rotation"));
        } catch (IllegalArgumentException e) {
            rotation = Rotation.NONE;
        }
        try {
            mode = Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.IDLE;
        }
        layer = tag.getInt("layer");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (blueprint != null) tag.putString("blueprint", blueprint.toString());
        tag.putString("rotation", rotation.name());
        tag.putString("mode", mode.name());
        tag.putInt("layer", layer);
        if (owner != null) tag.putUUID("owner", owner);
    }
}
