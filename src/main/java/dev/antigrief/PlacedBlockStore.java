package dev.antigrief;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread-only index, persisted inside each chunk alongside the blocks themselves. */
final class PlacedBlockStore {
    private final NamespacedKey dataKey;
    private final Map<ChunkKey, BitSet> loaded = new HashMap<>();

    PlacedBlockStore(NamespacedKey dataKey) {
        this.dataKey = dataKey;
    }

    boolean contains(Block block) {
        return positions(block.getChunk()).get(index(block));
    }

    void set(Block block, boolean placed) {
        Chunk chunk = block.getChunk();
        BitSet positions = positions(chunk);
        int index = index(block);
        if (positions.get(index) == placed) {
            return;
        }
        positions.set(index, placed);
        // Write through to PDC immediately, so normal chunk saves include the latest changes.
        var data = chunk.getPersistentDataContainer();
        if (positions.isEmpty()) {
            data.remove(dataKey);
        } else {
            data.set(dataKey, PersistentDataType.BYTE_ARRAY, positions.toByteArray());
        }
    }

    void unload(Chunk chunk) {
        loaded.remove(ChunkKey.of(chunk));
    }

    private BitSet positions(Chunk chunk) {
        return loaded.computeIfAbsent(ChunkKey.of(chunk), ignored -> {
            byte[] bytes = chunk.getPersistentDataContainer().get(dataKey, PersistentDataType.BYTE_ARRAY);
            return bytes == null ? new BitSet() : BitSet.valueOf(bytes);
        });
    }

    private static int index(Block block) {
        int height = block.getY() - block.getWorld().getMinHeight();
        return (height << 8) | ((block.getZ() & 15) << 4) | (block.getX() & 15);
    }

    private record ChunkKey(UUID world, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
