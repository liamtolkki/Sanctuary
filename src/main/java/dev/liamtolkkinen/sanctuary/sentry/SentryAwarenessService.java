package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import dev.liamtolkkinen.sanctuary.territory.TerritoryCalculator;
import dev.liamtolkkinen.sanctuary.upgrade.AnchorUpgradeType;
import dev.liamtolkkinen.sanctuary.upgrade.UpgradeRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Resolves the anchor-local awareness provided by Watcher's Eye upgrades.
 *
 * A Watcher's Eye never enables a whole Sanctuary globally. Only locations covered by an active
 * upgraded anchor are proactively watched, and Anchor Proximity uses the upgraded anchor's own
 * three-dimensional proximity sphere.
 */
public final class SentryAwarenessService {
    private static volatile SentryAwarenessService current;

    private final AnchorTerritoryService anchorTerritoryService;
    private final UpgradeRepository upgradeRepository;

    public SentryAwarenessService(
        AnchorTerritoryService anchorTerritoryService,
        UpgradeRepository upgradeRepository
    ) {
        this.anchorTerritoryService = Objects.requireNonNull(anchorTerritoryService, "anchorTerritoryService");
        this.upgradeRepository = Objects.requireNonNull(upgradeRepository, "upgradeRepository");
        current = this;
    }

    /**
     * Runtime-aware proactive check. A missing runtime instance preserves legacy/test behavior;
     * the plugin installs this service during startup before sentry tasks begin.
     */
    public static boolean proactivelyWatches(UUID sanctuaryId, Location location) throws SQLException {
        SentryAwarenessService service = current;
        return service == null || service.covers(sanctuaryId, location);
    }

    /** Runtime-aware Anchor Proximity check against Watcher's Eye anchors only. */
    public static boolean isNearWatcherAnchorRuntime(
        List<SanctuaryAnchor> activeAnchors,
        Location location,
        double radius
    ) throws SQLException {
        SentryAwarenessService service = current;
        if (service == null) return isNearAnyAnchor(activeAnchors, location, radius);
        return service.isNearWatcherAnchor(service.watcherAnchors(activeAnchors), location, radius);
    }

    public static boolean hasWatcherRuntime(SanctuaryAnchor anchor) throws SQLException {
        SentryAwarenessService service = current;
        return service == null || service.hasWatcher(anchor);
    }

    public List<SanctuaryAnchor> watcherAnchors(UUID sanctuaryId) throws SQLException {
        return watcherAnchors(anchorTerritoryService.activeAnchors(sanctuaryId));
    }

    public List<SanctuaryAnchor> watcherAnchors(List<SanctuaryAnchor> activeAnchors) throws SQLException {
        List<SanctuaryAnchor> result = new ArrayList<>();
        for (SanctuaryAnchor anchor : activeAnchors) {
            if (hasWatcher(anchor)) result.add(anchor);
        }
        return List.copyOf(result);
    }

    public boolean hasWatcher(SanctuaryAnchor anchor) throws SQLException {
        return upgradeRepository.hasAnchorUpgrade(anchor.id(), AnchorUpgradeType.WATCHERS_EYE);
    }

    /** Horizontal territory awareness, matching the Sanctuary territory model. */
    public boolean covers(List<SanctuaryAnchor> watcherAnchors, Location location) {
        if (location.getWorld() == null) return false;
        String world = location.getWorld().getName();
        for (SanctuaryAnchor anchor : watcherAnchors) {
            if (anchor.position().isEmpty()) continue;
            if (TerritoryCalculator.contains(
                anchor.position().orElseThrow(),
                anchor.territoryRadius(),
                world,
                location.getX(),
                location.getZ()
            )) {
                return true;
            }
        }
        return false;
    }

    public boolean covers(UUID sanctuaryId, Location location) throws SQLException {
        return covers(watcherAnchors(sanctuaryId), location);
    }

    /** Three-dimensional Anchor Proximity sphere centered on each Watcher's Eye anchor. */
    public boolean isNearWatcherAnchor(
        List<SanctuaryAnchor> watcherAnchors,
        Location location,
        double radius
    ) {
        return isNearAnyAnchor(watcherAnchors, location, radius);
    }

    private static boolean isNearAnyAnchor(
        List<SanctuaryAnchor> anchors,
        Location location,
        double radius
    ) {
        if (location.getWorld() == null) return false;
        double radiusSquared = radius * radius;
        String world = location.getWorld().getName();
        for (SanctuaryAnchor anchor : anchors) {
            if (anchor.position().isEmpty()) continue;
            var position = anchor.position().orElseThrow();
            if (!position.world().equals(world)) continue;
            double dx = location.getX() - (position.x() + 0.5);
            double dy = location.getY() - (position.y() + 0.5);
            double dz = location.getZ() - (position.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) return true;
        }
        return false;
    }
}
