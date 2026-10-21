package dev.antigrief;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PlacedBlockStoreTest {
    @Test
    void persistsThroughCacheEvictionAndPluginRestart() {
        var world = new TestWorld();
        var block = world.block(-1, -64, -17);
        world.store.set(block, true);
        world.store.unload(block.getChunk());
        assertTrue(world.store.contains(block));
        var restarted = new PlacedBlockStore(TestWorld.BLOCKS);
        assertTrue(restarted.contains(block));
        restarted.set(block, false);
        assertFalse(new PlacedBlockStore(TestWorld.BLOCKS).contains(block));
        assertNull(block.getChunk().getPersistentDataContainer()
                .get(TestWorld.BLOCKS, PersistentDataType.BYTE_ARRAY));
    }

    @Test
    void distinguishesHeightLocalCoordinatesChunkAndWorld() {
        var world = new TestWorld();
        var placed = world.block(-1, -64, -1);
        world.store.set(placed, true);
        world.store.set(world.block(-1, 319, -1), true);
        assertTrue(world.store.contains(placed));
        assertTrue(world.store.contains(world.block(-1, 319, -1)));
        assertFalse(world.store.contains(world.block(-1, -63, -1)));
        assertFalse(world.store.contains(world.block(-2, -64, -1)));
        assertFalse(world.store.contains(world.block(-1, -64, -2)));
        assertFalse(world.store.contains(world.block(15, -64, -1)));
        assertFalse(world.store.contains(new TestWorld().block(-1, -64, -1)));
    }
}
