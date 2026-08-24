package dev.liamtolkkinen.sanctuary.anchor;

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
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public final class AnchorBreakListener implements Listener {
    private final AnchorItemService anchorItemService;
    private final AnchorGraphService graphService;
    private final SanctuaryAnchorRepository anchorRepository;
    private final Logger logger;

    public AnchorBreakListener(
        AnchorItemService anchorItemService,
        AnchorGraphService graphService,
        SanctuaryAnchorRepository anchorRepository,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.graphService = graphService;
        this.anchorRepository = anchorRepository;
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
            if (anchorRepository.findById(metadata.anchorId()).isEmpty()) {
                event.setDropItems(false);
                event.getPlayer().sendMessage(
                    ChatColor.RED + "Warning: this Sanctuary anchor has no matching graph record. "
                        + "The orphaned anchor was destroyed and dropped nothing."
                );
                return;
            }

            GraphAnchorBreakResult result = graphService.breakAnchor(
                metadata,
                event.getPlayer().getUniqueId(),
                position,
                event.getPlayer().hasPermission("sanctuary.admin")
            );
            event.setDropItems(false);
            if (result.deleted()) {
                event.getPlayer().sendMessage(
                    ChatColor.YELLOW + result.sanctuary().name()
                        + " was an ephemeral debug Sanctuary and has been deleted."
                );
                return;
            }

            ItemStack boundAnchor = anchorItemService.createBound(
                result.sanctuary(),
                result.anchor()
            );
            Location dropLocation = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
            event.getBlock().getWorld().dropItemNaturally(dropLocation, boundAnchor);
            event.getPlayer().sendMessage(
                ChatColor.YELLOW + displayName(result.anchor()) + " is now inactive."
                    + (result.sanctuaryInactive()
                        ? " The Sanctuary has no active anchors."
                        : " The rest of the Sanctuary remains active.")
            );
        } catch (SQLException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                ChatColor.RED + "Sanctuary could not save this anchor break. Breaking was cancelled."
            );
            logger.log(Level.SEVERE, "Failed to deactivate Sanctuary anchor " + metadata.anchorId(), exception);
        } catch (AnchorPlacementException | IllegalStateException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + exception.getMessage());
            logger.log(Level.WARNING, "Rejected Sanctuary anchor break " + metadata.anchorId(), exception);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isManagedAnchor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isManagedAnchor)) {
            event.setCancelled(true);
        }
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
                GraphAnchorBreakResult result = graphService.breakAnchorFromEnvironment(metadata, position);
                affectedBlocks.remove(block);
                block.setType(Material.AIR, false);

                if (!result.deleted()) {
                    ItemStack item = anchorItemService.createBound(result.sanctuary(), result.anchor());
                    block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        item
                    );
                }
                logger.info(
                    "Sanctuary anchor " + metadata.anchorId() + " was broken by an explosion at "
                        + describe(position) + (result.deleted() ? " and was deleted." : "; anchor is inactive.")
                );
            } catch (SQLException exception) {
                affectedBlocks.remove(block);
                logger.log(
                    Level.SEVERE,
                    "Failed to deactivate exploded Sanctuary anchor " + metadata.anchorId(),
                    exception
                );
            } catch (AnchorPlacementException | IllegalStateException exception) {
                // Internal graph nodes are intentionally explosion-proof because removing one
                // would disconnect its descendants. Other lifecycle failures also fail closed.
                affectedBlocks.remove(block);
                logger.log(
                    Level.WARNING,
                    "Protected Sanctuary anchor " + metadata.anchorId() + " from explosion: "
                        + exception.getMessage()
                );
            }
        }
    }

    private boolean isManagedAnchor(Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return false;
        }
        return anchorItemService.readBlockMetadata(tileState).isPresent();
    }

    private static String displayName(SanctuaryAnchor anchor) {
        return anchor.type() == dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType.CONDUIT
            ? "Sanctuary Conduit"
            : "Sanctuary Beacon";
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
        return position.world() + " " + position.x() + "," + position.y() + "," + position.z();
    }
}
