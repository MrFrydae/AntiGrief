package dev.antigrief;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

final class BlockProtectionListener implements Listener {
    private final PlacedBlockStore placedBlocks;
    private final NamespacedKey fallingKey;

    BlockProtectionListener(PlacedBlockStore placedBlocks, NamespacedKey fallingKey) {
        this.placedBlocks = placedBlocks;
        this.fallingKey = fallingKey;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEndermanPickup(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman && event.getTo().isAir()
                && placedBlocks.contains(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multiPlace) {
            multiPlace.getReplacedBlockStates().forEach(state -> placedBlocks.set(state.getBlock(), true));
        } else {
            placedBlocks.set(event.getBlockPlaced(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        placedBlocks.set(event.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock falling) {
            var data = falling.getPersistentDataContainer();
            // The source becomes air, or water for a waterlogged gravity block.
            if (event.getTo().isAir() || event.getTo() == Material.WATER) {
                if (placedBlocks.contains(event.getBlock())) {
                    data.set(fallingKey, PersistentDataType.BYTE, (byte) 1);
                }
                placedBlocks.set(event.getBlock(), false);
            } else {
                placedBlocks.set(event.getBlock(), data.has(fallingKey, PersistentDataType.BYTE));
            }
        } else if (event.getEntity() instanceof Enderman || event.getTo().isAir()) {
            // Enderman-placed blocks are natural; other entity transformations retain provenance.
            placedBlocks.set(event.getBlock(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        // Paper supplies the movement direction, including on retraction.
        move(event.getBlocks(), event.getDirection());
    }

    private void move(List<Block> blocks, BlockFace direction) {
        var destinations = new ArrayList<Movement>();
        for (Block block : blocks) {
            // Paper's list includes blocks destroyed by the piston, such as mushrooms.
            if (block.getPistonMoveReaction() != PistonMoveReaction.BREAK) {
                destinations.add(new Movement(block.getRelative(direction), placedBlocks.contains(block)));
            }
        }
        // Snapshot before clearing: adjacent blocks' source/destination positions overlap.
        blocks.forEach(block -> placedBlocks.set(block, false));
        destinations.forEach(move -> placedBlocks.set(move.destination(), move.placed()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (destroysBlocks(event.getExplosionResult())) {
            event.blockList().forEach(block -> placedBlocks.set(block, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        // The exploding block itself is already gone.
        placedBlocks.set(event.getBlock(), false);
        if (destroysBlocks(event.getExplosionResult())) {
            event.blockList().forEach(block -> placedBlocks.set(block, false));
        }
    }

    private static boolean destroysBlocks(ExplosionResult result) {
        return result == ExplosionResult.DESTROY || result == ExplosionResult.DESTROY_WITH_DECAY;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        placedBlocks.set(event.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        placedBlocks.set(event.getBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getNewState().getType().isAir()) {
            placedBlocks.set(event.getBlock(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        placedBlocks.set(event.getToBlock(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        placedBlocks.unload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (var chunk : event.getWorld().getLoadedChunks()) {
            placedBlocks.unload(chunk);
        }
    }

    private record Movement(Block destination, boolean placed) {}
}
