package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
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
    private final InitialAnchorPlacementService initialPlacementService;
    private final AnchorLifecycleService lifecycleService;
    private final DoubleSupplier initialTerritoryRadius;
    private final DoubleSupplier maximumTerritoryRadius;
    private final DoubleSupplier spacingMargin;
    private final Logger logger;

    public AnchorPlacementListener(
        AnchorItemService anchorItemService,
        InitialAnchorPlacementService initialPlacementService,
        AnchorLifecycleService lifecycleService,
        DoubleSupplier initialTerritoryRadius,
        DoubleSupplier maximumTerritoryRadius,
        DoubleSupplier spacingMargin,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.initialPlacementService = initialPlacementService;
        this.lifecycleService = lifecycleService;
        this.initialTerritoryRadius = initialTerritoryRadius;
        this.maximumTerritoryRadius = maximumTerritoryRadius;
        this.spacingMargin = spacingMargin;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!anchorItemService.isSanctuaryBeacon(item)) {
            return;
        }

        Optional<AnchorMetadata> metadataResult = anchorItemService.readBeacon(item);
        if (metadataResult.isEmpty()) {
            reject(
                event,
                "This Sanctuary Beacon has invalid or incomplete metadata."
            );
            return;
        }

        if (!(event.getBlockPlaced().getState() instanceof TileState tileState)) {
            reject(event, "The placed Sanctuary Beacon cannot store anchor metadata.");
            return;
        }

        AnchorMetadata metadata = metadataResult.orElseThrow();
        SanctuaryPosition position = new SanctuaryPosition(
            event.getBlockPlaced().getWorld().getName(),
            event.getBlockPlaced().getX(),
            event.getBlockPlaced().getY(),
            event.getBlockPlaced().getZ()
        );

        try {
            if (metadata.isBound()) {
                anchorItemService.writeBlockMetadata(tileState, metadata);
                Sanctuary sanctuary = lifecycleService.reactivate(
                    metadata,
                    event.getPlayer().getUniqueId(),
                    position,
                    maximumTerritoryRadius.getAsDouble(),
                    spacingMargin.getAsDouble(),
                    event.getPlayer().hasPermission("sanctuary.admin")
                );
                event.getPlayer().sendMessage(
                    ChatColor.GREEN
                        + "Reactivated "
                        + sanctuary.name()
                        + ChatColor.GRAY
                        + " ("
                        + sanctuary.id()
                        + ")"
                );
                if (sanctuary.debugEphemeral()) {
                    event.getPlayer().sendMessage(
                        ChatColor.YELLOW
                            + "This is an ephemeral debug Sanctuary. Breaking it deletes it permanently."
                    );
                }
                return;
            }

            AnchorMetadata boundMetadata = metadata.bind(event.getPlayer().getUniqueId());
            anchorItemService.writeBlockMetadata(tileState, boundMetadata);
            Sanctuary sanctuary = initialPlacementService.createBeaconSanctuary(
                boundMetadata,
                event.getPlayer().getName(),
                position,
                initialTerritoryRadius.getAsDouble(),
                maximumTerritoryRadius.getAsDouble(),
                spacingMargin.getAsDouble()
            );

            event.getPlayer().sendMessage(
                ChatColor.GREEN
                    + "Created "
                    + sanctuary.name()
                    + ChatColor.GRAY
                    + " ("
                    + sanctuary.id()
                    + ")"
            );
        } catch (SQLException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                ChatColor.RED + "Sanctuary could not save this Beacon. Placement was cancelled."
            );
            logger.log(
                Level.SEVERE,
                "Failed to persist Sanctuary Beacon " + metadata.anchorId(),
                exception
            );
        } catch (AnchorPlacementException | IllegalStateException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + exception.getMessage());
            logger.log(
                Level.WARNING,
                "Rejected Sanctuary Beacon placement " + metadata.anchorId(),
                exception
            );
        }
    }

    private static void reject(BlockPlaceEvent event, String message) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + message);
    }
}
