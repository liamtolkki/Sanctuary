package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
        SanctuaryPosition position = position(event.getBlock());

        try {
            if (!lifecycleService.hasRegisteredSanctuary(metadata.anchorId())) {
                event.setDropItems(false);
                event.getPlayer().sendMessage(
                    ChatColor.RED
                        + "Warning: this Sanctuary Beacon has no matching database record. "
                        + "The orphaned Beacon was destroyed and dropped nothing."
                );
                logger.warning(
                    "Destroyed orphaned Sanctuary Beacon "
                        + metadata.anchorId()
                        + " at "
                        + describe(position)
                );
                return;
            }

            ItemStack boundBeacon = anchorItemService.createBoundBeacon(metadata.nextGeneration());
            AnchorBreakResult result = lifecycleService.breakAnchor(
                metadata,
                event.getPlayer().getUniqueId(),
                position,
                event.getPlayer().hasPermission("sanctuary.admin")
            );

            event.setDropItems(false);
            Sanctuary sanctuary = result.sanctuary();
            if (result.deleted()) {
                event.getPlayer().sendMessage(
                    ChatColor.YELLOW
                        + sanctuary.name()
                        + " was an ephemeral debug Sanctuary and has been deleted."
                );
                return;
            }

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> affectedBlocks) {
        for (Block block : List.copyOf(affectedBlocks)) {
            if (!(block.getState() instanceof TileState tileState)) {
                continue;
            }

            Optional<AnchorMetadata> metadataResult = anchorItemService.readBlockMetadata(tileState);
            if (metadataResult.isEmpty()) {
                continue;
            }

            AnchorMetadata metadata = metadataResult.orElseThrow();
            SanctuaryPosition position = position(block);

            try {
                AnchorBreakResult result = lifecycleService.breakAnchorFromEnvironment(
                    metadata,
                    position
                );

                // Sanctuary owns the block removal and replacement drop. Leaving the Beacon in
                // the vanilla explosion list would either duplicate the drop or lose its bound
                // Sanctuary metadata.
                affectedBlocks.remove(block);
                block.setType(Material.AIR, false);

                if (!result.deleted()) {
                    ItemStack boundBeacon = anchorItemService.createBoundBeacon(result.sanctuary());
                    block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        boundBeacon
                    );
                }

                logger.info(
                    "Sanctuary Beacon "
                        + metadata.anchorId()
                        + " was broken by an explosion at "
                        + describe(position)
                        + (result.deleted() ? " and its ephemeral Sanctuary was deleted." : "; Sanctuary is inactive.")
                );
            } catch (SQLException exception) {
                // Fail closed. Keep the physical Beacon if persistence could not be updated so
                // the world cannot drift away from the database state.
                affectedBlocks.remove(block);
                logger.log(
                    Level.SEVERE,
                    "Failed to deactivate exploded Sanctuary Beacon " + metadata.anchorId(),
                    exception
                );
            } catch (AnchorPlacementException | IllegalStateException exception) {
                affectedBlocks.remove(block);
                logger.log(
                    Level.WARNING,
                    "Rejected exploded Sanctuary Beacon lifecycle for " + metadata.anchorId(),
                    exception
                );
            }
        }
    }

    private static SanctuaryPosition position(Block block) {
        return new SanctuaryPosition(
            block.getWorld().getName(),
            block.getX(),
            block.getY(),
            block.getZ()
        );
    }

    private static String describe(SanctuaryPosition position) {
        return position.world()
            + " "
            + position.x()
            + ","
            + position.y()
            + ","
            + position.z();
    }
}
