package dev.antigrief;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class TestWorld {
    static final NamespacedKey BLOCKS = new NamespacedKey("antigrief", "placed_blocks_v1");
    static final NamespacedKey FALLING = new NamespacedKey("antigrief", "placed_falling_block");
    final World world = mock(World.class);
    final PlacedBlockStore store = new PlacedBlockStore(BLOCKS);
    final BlockProtectionListener listener = new BlockProtectionListener(store, FALLING);
    private final Map<String, Chunk> chunks = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();

    TestWorld() {
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getMinHeight()).thenReturn(-64);
    }

    Block block(int x, int y, int z) {
        String key = x + ":" + y + ":" + z;
        Block existing = blocks.get(key);
        if (existing != null) {
            return existing;
        }
        Block block = mock(Block.class);
        blocks.put(key, block);
        Chunk chunk = chunk(x >> 4, z >> 4);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getWorld()).thenReturn(world);
        when(block.getChunk()).thenReturn(chunk);
        when(block.getPistonMoveReaction()).thenReturn(PistonMoveReaction.MOVE);
        when(block.getRelative(any(BlockFace.class))).thenAnswer(call -> {
            BlockFace face = call.getArgument(0);
            return block(x + face.getModX(), y + face.getModY(), z + face.getModZ());
        });
        return block;
    }

    private Chunk chunk(int x, int z) {
        return chunks.computeIfAbsent(x + ":" + z, ignored -> {
            Chunk chunk = mock(Chunk.class);
            var data = container();
            when(chunk.getWorld()).thenReturn(world);
            when(chunk.getX()).thenReturn(x);
            when(chunk.getZ()).thenReturn(z);
            when(chunk.getPersistentDataContainer()).thenReturn(data);
            return chunk;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static PersistentDataContainer container() {
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Object> values = new HashMap<>();
        when(container.get(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenAnswer(call -> values.get(call.getArgument(0)));
        when(container.has(any(NamespacedKey.class), any(PersistentDataType.class)))
                .thenAnswer(call -> values.containsKey(call.getArgument(0)));
        doAnswer(call -> {
            Object value = call.getArgument(2);
            values.put(call.getArgument(0), value instanceof byte[] bytes ? bytes.clone() : value);
            return null;
        }).when(container).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
        doAnswer(call -> {
            values.remove(call.getArgument(0));
            return null;
        }).when(container).remove(any(NamespacedKey.class));
        return container;
    }
}
