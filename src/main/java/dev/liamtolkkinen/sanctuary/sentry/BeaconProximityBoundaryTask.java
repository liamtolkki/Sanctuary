package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import dev.liamtolkkinen.sanctuary.territory.AnchorTerritoryService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Draws the actual three-dimensional Beacon Proximity sentry trigger boundary.
 * This is intentionally separate from the Sanctuary territory union perimeter.
 */
public final class BeaconProximityBoundaryTask implements Runnable {
    private static final long LINGER_MILLIS = 3000L;

    private final SanctuaryRepository sanctuaryRepository;
    private final AnchorTerritoryService anchorTerritoryService;
    private final SentryRepository sentryRepository;
    private final SentryService sentryService;
    private final DoubleSupplier particleSpacing;
    private final DoubleSupplier minimumViewerDistance;
    private final DoubleSupplier maximumViewerDistance;
    private final LongSupplier updatePeriodTicks;
    private final Logger logger;
    private final Map<BoundaryViewerKey, Long> visibleUntil = new HashMap<>();

    public BeaconProximityBoundaryTask(
        SanctuaryRepository sanctuaryRepository,
        AnchorTerritoryService anchorTerritoryService,
        SentryRepository sentryRepository,
        SentryService sentryService,
        DoubleSupplier particleSpacing,
        DoubleSupplier minimumViewerDistance,
        DoubleSupplier maximumViewerDistance,
        LongSupplier updatePeriodTicks,
        Logger logger
    ) {
        this.sanctuaryRepository = sanctuaryRepository;
        this.anchorTerritoryService = anchorTerritoryService;
        this.sentryRepository = sentryRepository;
        this.sentryService = sentryService;
        this.particleSpacing = particleSpacing;
        this.minimumViewerDistance = minimumViewerDistance;
        this.maximumViewerDistance = maximumViewerDistance;
        this.updatePeriodTicks = updatePeriodTicks;
        this.logger = logger;
    }

    public void start(JavaPlugin plugin) {
        long period = Math.max(1L, updatePeriodTicks.getAsLong());
        Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        try {
            for (Sanctuary sanctuary : sanctuaryRepository.findAll()) {
                if (sanctuary.state() != SanctuaryState.ACTIVE) {
                    continue;
                }

                List<SentryRecord> proximitySentries = activeProximitySentries(sanctuary);
                if (proximitySentries.isEmpty()) {
                    continue;
                }

                for (SanctuaryAnchor anchor : anchorTerritoryService.activeAnchors(sanctuary.id())) {
                    if (anchor.type() != SanctuaryType.BEACON || anchor.position().isEmpty()) {
                        continue;
                    }
                    renderAnchorSphere(sanctuary, anchor, proximitySentries, now);
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to render Beacon Proximity boundaries", exception);
        } finally {
            visibleUntil.entrySet().removeIf(entry -> entry.getValue() < now);
        }
    }

    private List<SentryRecord> activeProximitySentries(Sanctuary sanctuary) throws SQLException {
        List<SentryRecord> result = new ArrayList<>();
        for (SentryRecord sentry : sentryRepository.findBySanctuary(sanctuary.id())) {
            if (sentry.state() == SentryState.ACTIVE
                && sentryService.effective(sentry, SentryTrigger.BEACON_PROXIMITY)) {
                result.add(sentry);
            }
        }
        return List.copyOf(result);
    }

    private void renderAnchorSphere(
        Sanctuary sanctuary,
        SanctuaryAnchor anchor,
        List<SentryRecord> proximitySentries,
        long now
    ) {
        var position = anchor.position().orElseThrow();
        World world = Bukkit.getWorld(position.world());
        if (world == null) {
            return;
        }

        Location center = new Location(
            world,
            position.x() + 0.5,
            position.y() + 0.5,
            position.z() + 0.5
        );
        double radius = SentryService.BEACON_PROXIMITY_RADIUS;
        double maximum = Math.max(
            Math.max(0.0, minimumViewerDistance.getAsDouble()),
            maximumViewerDistance.getAsDouble()
        );
        double spacing = Math.max(0.5, particleSpacing.getAsDouble());

        for (Player player : world.getPlayers()) {
            BoundaryViewerKey key = new BoundaryViewerKey(anchor.id(), player.getUniqueId());
            double centerDistance = player.getLocation().distance(center);
            boolean inRange = Math.abs(centerDistance - radius) <= maximum;
            if (inRange) {
                visibleUntil.put(key, now + LINGER_MILLIS);
            }

            Long expiry = visibleUntil.get(key);
            if (!inRange && (expiry == null || expiry < now)) {
                continue;
            }

            boolean dangerous = wouldEngage(sanctuary, proximitySentries, player);
            Particle.DustOptions dust = new Particle.DustOptions(
                dangerous ? Color.RED : Color.WHITE,
                1.0f
            );
            renderFullSphere(player, center, radius, spacing, dust);
        }
    }

    private boolean wouldEngage(
        Sanctuary sanctuary,
        List<SentryRecord> proximitySentries,
        Player player
    ) {
        for (SentryRecord sentry : proximitySentries) {
            SentryDefinition definition = sentryService.definition(sentry).orElse(null);
            if (definition != null && sentryService.validTarget(sanctuary, sentry, definition, player)) {
                return true;
            }
        }
        return false;
    }

    private static void renderFullSphere(
        Player player,
        Location center,
        double radius,
        double spacing,
        Particle.DustOptions dust
    ) {
        double latitudeStep = Math.max(0.08, spacing / radius);

        for (double latitude = -Math.PI / 2.0;
             latitude <= Math.PI / 2.0 + 1.0e-9;
             latitude += latitudeStep) {
            double ringRadius = radius * Math.cos(latitude);
            double y = center.getY() + radius * Math.sin(latitude);
            int ringPoints = Math.max(
                1,
                (int) Math.ceil((Math.PI * 2.0 * Math.max(ringRadius, spacing)) / spacing)
            );
            for (int index = 0; index < ringPoints; index++) {
                double angle = Math.PI * 2.0 * index / ringPoints;
                double x = center.getX() + ringRadius * Math.cos(angle);
                double z = center.getZ() + ringRadius * Math.sin(angle);
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    private record BoundaryViewerKey(UUID anchorId, UUID playerId) {
    }
}
