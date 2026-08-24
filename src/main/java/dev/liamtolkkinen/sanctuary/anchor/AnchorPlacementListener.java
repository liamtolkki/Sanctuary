package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public final class AnchorPlacementListener implements Listener {
    private final AnchorItemService anchorItemService;
    private final AnchorGraphService graphService;
    private final DoubleSupplier initialTerritoryRadius;
    private final DoubleSupplier maximumTerritoryRadius;
    private final DoubleSupplier spacingMargin;
    private final Logger logger;

    public AnchorPlacementListener(
        AnchorItemService anchorItemService,
        AnchorGraphService graphService,
        DoubleSupplier initialTerritoryRadius,
        DoubleSupplier maximumTerritoryRadius,
        DoubleSupplier spacingMargin,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.graphService = graphService;
        this.initialTerritoryRadius = initialTerritoryRadius;
        this.maximumTerritoryRadius = maximumTerritoryRadius;
        this.spacingMargin = spacingMargin;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<SanctuaryType> typeResult = anchorItemService.anchorType(item);
        if (typeResult.isEmpty()) {
            return;
        }

        Optional<AnchorMetadata> metadataResult = anchorItemService.readOrInitializeUnboundAnchor(item);
        if (metadataResult.isEmpty()) {
            reject(event, "This Sanctuary anchor has invalid or incomplete metadata.");
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof TileState tileState)) {
            reject(event, "The placed Sanctuary anchor cannot store anchor metadata.");
            return;
        }

        SanctuaryType type = typeResult.orElseThrow();
        AnchorMetadata metadata = metadataResult.orElseThrow();
        SanctuaryPosition position = new SanctuaryPosition(
            event.getBlockPlaced().getWorld().getName(),
            event.getBlockPlaced().getX(),
            event.getBlockPlaced().getY(),
            event.getBlockPlaced().getZ()
        );

        try {
            boolean registeredAnchor = graphService.isRegisteredAnchor(metadata.anchorId());

            // Anchor ownership follows placement, not item provenance. The physical anchor keeps
            // its identity and upgrades, while the placer determines the Sanctuary it joins.
            AnchorMetadata blockMetadata = new AnchorMetadata(
                metadata.anchorId(),
                Optional.of(event.getPlayer().getUniqueId()),
                metadata.tier(),
                metadata.generation()
            );

            // Persist block identity before the graph mutation. If this fails, the database is
            // untouched and Bukkit can safely roll the placement back.
            anchorItemService.writeBlockMetadata(tileState, blockMetadata);

            AnchorPlacementOutcome outcome;
            if (registeredAnchor) {
                outcome = graphService.placeBound(
                    blockMetadata,
                    type,
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getName(),
                    position,
                    maximumTerritoryRadius.getAsDouble(),
                    spacingMargin.getAsDouble(),
                    event.getPlayer().hasPermission("sanctuary.admin")
                );
            } else {
                outcome = graphService.placeNew(
                    blockMetadata,
                    type,
                    event.getPlayer().getUniqueId(),
                    event.getPlayer().getName(),
                    position,
                    initialTerritoryRadius.getAsDouble(),
                    maximumTerritoryRadius.getAsDouble(),
                    spacingMargin.getAsDouble()
                );
            }

            String anchorName = displayName(type);
            if (outcome.joinedExistingSanctuary()) {
                event.getPlayer().sendMessage(
                    ChatColor.AQUA + anchorName + ChatColor.GREEN + " joined "
                        + outcome.sanctuary().name() + ChatColor.GRAY
                        + " as a Sanctuary extender."
                );
            } else {
                event.getPlayer().sendMessage(
                    ChatColor.GREEN + "Activated " + outcome.sanctuary().name()
                        + ChatColor.GRAY + " with a " + anchorName + "."
                );
            }
            if (outcome.sourceSanctuaryDeleted()) {
                event.getPlayer().sendMessage(
                    ChatColor.GRAY + "The anchor's previous empty Sanctuary was removed."
                );
            }
        } catch (SQLException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                ChatColor.RED + "Sanctuary could not save this anchor. Placement was cancelled."
            );
            logger.log(Level.SEVERE, "Failed to persist Sanctuary anchor " + metadata.anchorId(), exception);
        } catch (AnchorPlacementException | IllegalStateException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + exception.getMessage());
            logger.log(Level.WARNING, "Rejected Sanctuary anchor placement " + metadata.anchorId(), exception);
        }
    }

    private static String displayName(SanctuaryType type) {
        return type == SanctuaryType.CONDUIT ? "Sanctuary Conduit" : "Sanctuary Beacon";
    }

    private static void reject(BlockPlaceEvent event, String message) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + message);
    }
}
