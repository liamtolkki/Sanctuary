package dev.liamtolkkinen.sanctuary.anchor;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

public final class AnchorItemRemovalListener implements Listener {
    private final AnchorItemService anchorItemService;
    private final AnchorGraphService graphService;
    private final Logger logger;

    public AnchorItemRemovalListener(
        AnchorItemService anchorItemService,
        AnchorGraphService graphService,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.graphService = graphService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }

        Optional<String> destructionReason = destructionReason(event.getCause());
        if (destructionReason.isEmpty()) {
            return;
        }

        Optional<AnchorMetadata> metadataResult = anchorItemService.readAnchor(item.getItemStack());
        if (metadataResult.isEmpty() || !metadataResult.orElseThrow().isBound()) {
            return;
        }

        AnchorMetadata metadata = metadataResult.orElseThrow();
        try {
            graphService.recordDestruction(metadata, destructionReason.orElseThrow())
                .ifPresent(anchor -> logger.warning(
                    "Sanctuary " + anchor.type().name().toLowerCase(java.util.Locale.ROOT)
                        + " anchor " + anchor.id() + " was permanently destroyed: "
                        + anchor.destructionReason().orElse("unknown")
                ));
        } catch (SQLException exception) {
            logger.log(
                Level.SEVERE,
                "Failed to record destruction of Sanctuary anchor " + metadata.anchorId(),
                exception
            );
        }
    }

    private static Optional<String> destructionReason(EntityRemoveEvent.Cause cause) {
        return switch (cause) {
            case DEATH -> Optional.of("DEATH");
            case DESPAWN -> Optional.of("DESPAWN");
            case OUT_OF_WORLD -> Optional.of("OUT_OF_WORLD");
            case PLUGIN -> Optional.of("PLUGIN_REMOVAL");
            case DISCARD -> Optional.of("DISCARD");
            case EXPLODE -> Optional.of("EXPLOSION");
            default -> Optional.empty();
        };
    }
}
