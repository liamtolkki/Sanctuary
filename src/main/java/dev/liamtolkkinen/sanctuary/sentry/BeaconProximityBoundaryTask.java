package dev.liamtolkkinen.sanctuary.sentry;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryRepository;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
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
 * Draws the three-dimensional Anchor Proximity trigger as the outer surface of
 * the union of all Watcher's Eye anchor spheres in a Sanctuary.
 */
public final class BeaconProximityBoundaryTask implements Runnable {
    private static final long LINGER_MILLIS = 3000L;
    private static final double UNION_EPSILON = 1.0e-6;

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

                List<SanctuaryAnchor> watcherAnchors = anchorTerritoryService.activeAnchors(sanctuary.id()).stream()
                    .filter(anchor -> anchor.position().isPresent())
                    .filter(SentryAwarenessService::hasWatcherRuntime)
                    .toList();
                if (watcherAnchors.isEmpty()) {
                    continue;
                }

                renderUnion(sanctuary, watcherAnchors, proximitySentries, now);
            }
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to render Anchor Proximity boundaries", exception);
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

    private void renderUnion(
        Sanctuary sanctuary,
        List<SanctuaryAnchor> watcherAnchors,
        List<SentryRecord> proximitySentries,
        long now
    ) {
        Map<UUID, Sphere> spheres = new HashMap<>();
        for (SanctuaryAnchor anchor : watcherAnchors) {
            var position = anchor.position().orElseThrow();
            World world = Bukkit.getWorld(position.world());
            if (world == null) {
                continue;
            }
            spheres.put(anchor.id(), new Sphere(
                anchor.id(),
                new Location(world, position.x() + 0.5, position.y() + 0.5, position.z() + 0.5),
                SentryService.BEACON_PROXIMITY_RADIUS
            ));
        }
        if (spheres.isEmpty()) {
            return;
        }

        double maximum = Math.max(
            Math.max(0.0, minimumViewerDistance.getAsDouble()),
            maximumViewerDistance.getAsDouble()
        );
        double spacing = Math.max(0.5, particleSpacing.getAsDouble());

        for (Sphere sphere : spheres.values()) {
            World world = sphere.center().getWorld();
            if (world == null) {
                continue;
            }
            List<Sphere> sameWorldSpheres = spheres.values().stream()
                .filter(other -> other.center().getWorld() == world)
                .toList();

            for (Player player : world.getPlayers()) {
                BoundaryViewerKey key = new BoundaryViewerKey(sphere.anchorId(), player.getUniqueId());
                double centerDistance = player.getLocation().distance(sphere.center());
                boolean inRange = Math.abs(centerDistance - sphere.radius()) <= maximum;
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
                renderUnionSphere(player, sphere, sameWorldSpheres, spacing, dust);
            }
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

    private static void renderUnionSphere(
        Player player,
        Sphere sphere,
        List<Sphere> unionSpheres,
        double spacing,
        Particle.DustOptions dust
    ) {
        Location center = sphere.center();
        double radius = sphere.radius();
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
                if (insideAnotherSphere(sphere, unionSpheres, x, y, z)) {
                    continue;
                }
                player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    private static boolean insideAnotherSphere(
        Sphere source,
        List<Sphere> spheres,
        double x,
        double y,
        double z
    ) {
        for (Sphere other : spheres) {
            if (other.anchorId().equals(source.anchorId())) {
                continue;
            }
            Location center = other.center();
            double dx = x - center.getX();
            double dy = y - center.getY();
            double dz = z - center.getZ();
            double interiorRadius = Math.max(0.0, other.radius() - UNION_EPSILON);
            if (dx * dx + dy * dy + dz * dz < interiorRadius * interiorRadius) {
                return true;
            }
        }
        return false;
    }

    private record Sphere(UUID anchorId, Location center, double radius) {
    }

    private record BoundaryViewerKey(UUID anchorId, UUID playerId) {
    }
}
