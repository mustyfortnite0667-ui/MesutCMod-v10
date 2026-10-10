package net.friends.mesutcmod.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;

public final class MesutChestLootState extends PersistentState {
    private static final String KEY = "AssignedChests";
    private final Set<Long> assigned = new HashSet<>();

    public static MesutChestLootState fromNbt(NbtCompound nbt) {
        MesutChestLootState state = new MesutChestLootState();
        for (long value : nbt.getLongArray(KEY)) state.assigned.add(value);
        return state;
    }

    public static MesutChestLootState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                MesutChestLootState::fromNbt,
                MesutChestLootState::new,
                "mesutcmod_mesut_chests"
        );
    }

    public boolean isAssigned(BlockPos pos) {
        return assigned.contains(pos.asLong());
    }

    public void markAssigned(BlockPos pos) {
        if (assigned.add(pos.asLong())) markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        long[] values = new long[assigned.size()];
        int i = 0;
        for (long value : assigned) values[i++] = value;
        nbt.putLongArray(KEY, values);
        return nbt;
    }
}
