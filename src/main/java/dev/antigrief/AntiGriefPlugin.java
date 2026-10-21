package dev.antigrief;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiGriefPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        var placedBlocks = new PlacedBlockStore(new NamespacedKey(this, "placed_blocks_v1"));
        getServer().getPluginManager().registerEvents(
                new BlockProtectionListener(placedBlocks, new NamespacedKey(this, "placed_falling_block")), this);
        getLogger().info("Enderman protection enabled for newly placed player blocks.");
    }
}
