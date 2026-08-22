package dev.liamtolkkinen.sanctuary.anchor;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
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
    private final AnchorLifecycleService lifecycleService;
    private final Logger logger;

    public AnchorItemRemovalListener(
        AnchorItemService anchorItemService,
        AnchorLifecycleService lifecycleService,
        Logger logger
    ) {
        this.anchorItemService = anchorItemService;
        this.lifecycleService = lifecycleService;
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

        Optional<AnchorMetadata> metadataResult = anchorItemService.readBeacon(item.getItemStack());
        if (metadataResult.isEmpty() || !metadataResult.orElseThrow().isBound()) {
            return;
        }

        AnchorMetadata metadata = metadataResult.orElseThrow();
        try {
            Optional<Sanctuary> destroyed = lifecycleService.recordDestruction(
                metadata,
                destructionReason.orElseThrow()
            );
            destroyed.ifPresent(sanctuary -> logger.warning(
                "Sanctuary Beacon "
                    + sanctuary.id()
                    + " was permanently destroyed: "
                    + sanctuary.destructionReason().orElse("unknown")
            ));
        } catch (SQLException exception) {
            logger.log(
                Level.SEVERE,
                "Failed to record destruction of Sanctuary Beacon " + metadata.anchorId(),
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
