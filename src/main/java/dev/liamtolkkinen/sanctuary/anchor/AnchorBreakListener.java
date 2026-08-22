package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class AnchorBreakListener implements Listener {
    private final AnchorItemService anchorItemService;
    private final AnchorLifecycleService lifecycleService;
    private final Logger logger;

    public AnchorBreakListener(
        AnchorItemService anchorItemService,
        AnchorLifecycleService lifecycleService,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.lifecycleService = lifecycleService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof TileState tileState)) {
            return;
        }

        Optional<AnchorMetadata> metadataResult = anchorItemService.readBlockMetadata(tileState);
        if (metadataResult.isEmpty()) {
            return;
        }

        AnchorMetadata metadata = metadataResult.orElseThrow();
        SanctuaryPosition position = new SanctuaryPosition(
            event.getBlock().getWorld().getName(),
            event.getBlock().getX(),
            event.getBlock().getY(),
            event.getBlock().getZ()
        );

        try {
            AnchorMetadata droppedMetadata = metadata.nextGeneration();
            ItemStack boundBeacon = anchorItemService.createBoundBeacon(droppedMetadata);
            Sanctuary sanctuary = lifecycleService.deactivateForBreak(
                metadata,
                event.getPlayer().getUniqueId(),
                position,
                event.getPlayer().hasPermission("sanctuary.admin")
            );

            event.setDropItems(false);
            Location dropLocation = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
            event.getBlock().getWorld().dropItemNaturally(dropLocation, boundBeacon);

            event.getPlayer().sendMessage(
                ChatColor.YELLOW
                    + sanctuary.name()
                    + " is now inactive. Keep its Sanctuary Beacon safe."
            );
        } catch (SQLException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                ChatColor.RED + "Sanctuary could not save this Beacon break. Breaking was cancelled."
            );
            logger.log(
                Level.SEVERE,
                "Failed to deactivate Sanctuary Beacon " + metadata.anchorId(),
                exception
            );
        } catch (AnchorPlacementException | IllegalStateException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + exception.getMessage());
            logger.log(
                Level.WARNING,
                "Rejected Sanctuary Beacon break " + metadata.anchorId(),
                exception
            );
        }
    }
}
