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
    private final InitialAnchorPlacementService placementService;
    private final DoubleSupplier initialTerritoryArea;
    private final Logger logger;

    public AnchorPlacementListener(
        AnchorItemService anchorItemService,
        InitialAnchorPlacementService placementService,
        DoubleSupplier initialTerritoryArea,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.placementService = placementService;
        this.initialTerritoryArea = initialTerritoryArea;
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

        AnchorMetadata metadata = metadataResult.orElseThrow();
        if (metadata.isBound()) {
            reject(
                event,
                "This Sanctuary Beacon is already bound. Re-placement is not available yet."
            );
            return;
        }

        if (!(event.getBlockPlaced().getState() instanceof TileState tileState)) {
            reject(event, "The placed Sanctuary Beacon cannot store anchor metadata.");
            return;
        }

        AnchorMetadata boundMetadata = metadata.bind(event.getPlayer().getUniqueId());
        SanctuaryPosition position = new SanctuaryPosition(
            event.getBlockPlaced().getWorld().getName(),
            event.getBlockPlaced().getX(),
            event.getBlockPlaced().getY(),
            event.getBlockPlaced().getZ()
        );

        try {
            anchorItemService.writeBlockMetadata(tileState, boundMetadata);

            Sanctuary sanctuary = placementService.createBeaconSanctuary(
                boundMetadata,
                event.getPlayer().getName(),
                position,
                initialTerritoryArea.getAsDouble()
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
