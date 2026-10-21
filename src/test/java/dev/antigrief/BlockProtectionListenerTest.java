package dev.antigrief;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import java.util.List;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.Test;

class BlockProtectionListenerTest {
    private final TestWorld world = new TestWorld();
    private final Block block = world.block(0, 64, 0);

    @Test
    void playerPlacementBlocksEndermanPickupButNotNaturalBlocksOrPlacement() {
        var placement = mock(BlockPlaceEvent.class);
        when(placement.canBuild()).thenReturn(true);
        when(placement.getBlockPlaced()).thenReturn(block);
        world.listener.onPlace(placement);
        var pickup = change(mock(Enderman.class), block, Material.AIR);
        world.listener.onEndermanPickup(pickup);
        verify(pickup).setCancelled(true);

        var naturalPickup = change(mock(Enderman.class), world.block(1, 64, 0), Material.AIR);
        world.listener.onEndermanPickup(naturalPickup);
        verify(naturalPickup, never()).setCancelled(true);
        var endermanPlacement = change(mock(Enderman.class), block, Material.DIRT);
        world.listener.onEndermanPickup(endermanPlacement);
        verify(endermanPlacement, never()).setCancelled(true);
    }

    @Test
    void otherMobsAreUnaffectedAndSuccessfulBreakClearsProtection() {
        world.store.set(block, true);
        var otherMob = change(mock(Entity.class), block, Material.AIR);
        world.listener.onEndermanPickup(otherMob);
        verify(otherMob, never()).setCancelled(true);
        var broken = mock(BlockBreakEvent.class);
        when(broken.getBlock()).thenReturn(block);
        world.listener.onBreak(broken);
        assertFalse(world.store.contains(block));
    }

    @Test
    void cancelledPlacementAndBreakLeaveProvenanceUnchanged() throws Exception {
        var placement = mock(BlockPlaceEvent.class);
        when(placement.isCancelled()).thenReturn(true);
        dispatch("onPlace", BlockPlaceEvent.class, placement);
        assertFalse(world.store.contains(block));

        world.store.set(block, true);
        var broken = mock(BlockBreakEvent.class);
        when(broken.isCancelled()).thenReturn(true);
        dispatch("onBreak", BlockBreakEvent.class, broken);
        assertTrue(world.store.contains(block));
    }

    @Test
    void deniedBuildDoesNotCreateProtection() {
        var placement = mock(BlockPlaceEvent.class);
        when(placement.canBuild()).thenReturn(false);
        world.listener.onPlace(placement);
        assertFalse(world.store.contains(block));
    }

    @Test
    void multiPlaceTracksEveryBlock() {
        var first = mock(BlockState.class);
        var second = mock(BlockState.class);
        var upper = world.block(0, 65, 0);
        when(first.getBlock()).thenReturn(block);
        when(second.getBlock()).thenReturn(upper);
        var placement = mock(BlockMultiPlaceEvent.class);
        when(placement.canBuild()).thenReturn(true);
        when(placement.getReplacedBlockStates()).thenReturn(List.of(first, second));
        world.listener.onPlace(placement);
        assertTrue(world.store.contains(block));
        assertTrue(world.store.contains(second.getBlock()));
    }

    @Test
    void pistonSnapshotsOverlappingPositionsAndCrossesChunkBoundary() {
        var first = world.block(15, 64, 0);
        var second = world.block(16, 64, 0);
        var third = world.block(17, 64, 0);
        world.store.set(first, true);
        // A stale destination must not make a natural moved block protected.
        world.store.set(third, true);
        var event = mock(BlockPistonExtendEvent.class);
        when(event.getBlocks()).thenReturn(List.of(first, second));
        when(event.getDirection()).thenReturn(BlockFace.EAST);
        world.listener.onExtend(event);
        assertFalse(world.store.contains(first));
        assertTrue(world.store.contains(second));
        assertFalse(world.store.contains(third));
    }

    @Test
    void stickyRetractionUsesActualMovementDirection() {
        world.store.set(block, true);
        var event = mock(BlockPistonRetractEvent.class);
        when(event.getBlocks()).thenReturn(List.of(block));
        when(event.getDirection()).thenReturn(BlockFace.WEST);
        world.listener.onRetract(event);
        assertFalse(world.store.contains(block));
        assertTrue(world.store.contains(world.block(-1, 64, 0)));
    }

    @Test
    void pistonDestroyedBlocksDoNotTransferProtection() {
        world.store.set(block, true);
        when(block.getPistonMoveReaction()).thenReturn(PistonMoveReaction.BREAK);
        var event = mock(BlockPistonExtendEvent.class);
        when(event.getBlocks()).thenReturn(List.of(block));
        when(event.getDirection()).thenReturn(BlockFace.UP);
        world.listener.onExtend(event);
        assertFalse(world.store.contains(block));
        assertFalse(world.store.contains(world.block(0, 65, 0)));
    }

    @Test
    void protectedFallingBlockCarriesProtectionIntoAnotherChunk() {
        world.store.set(block, true);
        var falling = mock(FallingBlock.class);
        var data = TestWorld.container();
        when(falling.getPersistentDataContainer()).thenReturn(data);
        world.listener.onEntityChange(change(falling, block, Material.AIR));
        assertFalse(world.store.contains(block));
        var destination = world.block(16, 60, 0);
        world.listener.onEntityChange(change(falling, destination, Material.SAND));
        assertTrue(new PlacedBlockStore(TestWorld.BLOCKS).contains(destination));
    }

    @Test
    void naturalFallingBlockAndEndermanPlacementDoNotInheritOldProtection() {
        var falling = mock(FallingBlock.class);
        var data = TestWorld.container();
        when(falling.getPersistentDataContainer()).thenReturn(data);
        world.store.set(block, true);
        world.listener.onEntityChange(change(falling, block, Material.SAND));
        assertFalse(world.store.contains(block));
        world.store.set(block, true);
        world.listener.onEntityChange(change(mock(Enderman.class), block, Material.DIRT));
        assertFalse(world.store.contains(block));
    }

    @Test
    void destructiveExplosionsClearOnlyTheirFinalAffectedList() {
        world.store.set(block, true);
        var excluded = world.block(1, 64, 0);
        world.store.set(excluded, true);
        var event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(List.of(block));
        when(event.getExplosionResult()).thenReturn(ExplosionResult.KEEP);
        world.listener.onEntityExplosion(event);
        assertTrue(world.store.contains(block));
        when(event.getExplosionResult()).thenReturn(ExplosionResult.DESTROY);
        world.listener.onEntityExplosion(event);
        assertFalse(world.store.contains(block));
        assertTrue(world.store.contains(excluded));
    }

    @Test
    void fadingTransformationRetainsProtectionAndPhysicsDestructionClearsIt() {
        world.store.set(block, true);
        var state = mock(BlockState.class);
        Material dirt = material(Material.DIRT);
        when(state.getType()).thenReturn(dirt);
        var event = mock(BlockFadeEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getNewState()).thenReturn(state);
        world.listener.onFade(event);
        assertTrue(world.store.contains(block));
        var destroyed = mock(BlockDestroyEvent.class);
        when(destroyed.getBlock()).thenReturn(block);
        world.listener.onDestroy(destroyed);
        assertFalse(world.store.contains(block));
    }

    private EntityChangeBlockEvent change(Entity entity, Block block, Material to) {
        var event = mock(EntityChangeBlockEvent.class);
        Material result = material(to);
        when(event.getEntity()).thenReturn(entity);
        when(event.getBlock()).thenReturn(block);
        when(event.getTo()).thenReturn(result);
        return event;
    }

    private Material material(Material type) {
        // Paper delegates Material.isAir() to live server registries. Supply that
        // API response explicitly for these isolated event-listener tests.
        Material result = mock(Material.class, type.name());
        when(result.isAir()).thenReturn(type == Material.AIR
                || type == Material.CAVE_AIR || type == Material.VOID_AIR);
        return result;
    }

    private <T extends Event> void dispatch(String name, Class<T> type, T event) throws Exception {
        var method = BlockProtectionListener.class.getMethod(name, type);
        var handler = method.getAnnotation(EventHandler.class);
        var registration = new RegisteredListener(world.listener,
                (listener, fired) -> {
                    try {
                        method.invoke(listener, fired);
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                }, handler.priority(), mock(Plugin.class), handler.ignoreCancelled());
        registration.callEvent(event);
    }
}
