package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.security.SanctuaryRelationship;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TerritoryBoundaryService {
    private static final double UNION_EPSILON = 0.08;

    private final JavaPlugin plugin;
    private final SanctuarySecurityService securityService;
    private final AnchorTerritoryService anchorTerritoryService;
    private final Supplier<Particle> ownerParticle;
    private final Supplier<Particle> trustedParticle;
    private final Supplier<Particle> neutralParticle;
    private final Supplier<Particle> hostileParticle;
    private final Logger logger;

    public TerritoryBoundaryService(
        JavaPlugin plugin,
        SanctuarySecurityService securityService,
        Supplier<Particle> ownerParticle,
        Supplier<Particle> trustedParticle,
        Supplier<Particle> neutralParticle,
        Supplier<Particle> hostileParticle,
        Logger logger
    ) {
        this(plugin, securityService, null, ownerParticle, trustedParticle, neutralParticle, hostileParticle, logger);
    }

    public TerritoryBoundaryService(
        JavaPlugin plugin,
        SanctuarySecurityService securityService,
        AnchorTerritoryService anchorTerritoryService,
        Supplier<Particle> ownerParticle,
        Supplier<Particle> trustedParticle,
        Supplier<Particle> neutralParticle,
        Supplier<Particle> hostileParticle,
        Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.securityService = Objects.requireNonNull(securityService, "securityService");
        this.anchorTerritoryService = anchorTerritoryService;
        this.ownerParticle = Objects.requireNonNull(ownerParticle, "ownerParticle");
        this.trustedParticle = Objects.requireNonNull(trustedParticle, "trustedParticle");
        this.neutralParticle = Objects.requireNonNull(neutralParticle, "neutralParticle");
        this.hostileParticle = Objects.requireNonNull(hostileParticle, "hostileParticle");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public BukkitTask show(Player viewer, Sanctuary sanctuary, double particleSpacing, int displaySeconds) {
        Objects.requireNonNull(viewer, "viewer");
        validateManualArguments(sanctuary, particleSpacing, displaySeconds);
        return schedule(viewer, List.of(sanctuary), particleSpacing, displaySeconds, Double.POSITIVE_INFINITY);
    }

    public BukkitTask showAll(
        Player viewer,
        List<Sanctuary> sanctuaries,
        double particleSpacing,
        int displaySeconds,
        double maximumRenderDistance
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(sanctuaries, "sanctuaries");
        if (!Double.isFinite(maximumRenderDistance) || maximumRenderDistance <= 0.0) {
            throw new IllegalArgumentException("maximumRenderDistance must be finite and greater than zero");
        }
        sanctuaries.forEach(value -> validateManualArguments(value, particleSpacing, displaySeconds));
        return schedule(viewer, List.copyOf(sanctuaries), particleSpacing, displaySeconds, maximumRenderDistance);
    }

    public void showProximity(
        Player viewer,
        Sanctuary sanctuary,
        double horizontalSpacing,
        double verticalSpacing,
        double minimumDistance,
        double maximumDistance
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(sanctuary, "sanctuary");
        validateSpacing(horizontalSpacing, "horizontalSpacing");
        validateSpacing(verticalSpacing, "verticalSpacing");
        validateProximityDistances(minimumDistance, maximumDistance);
        try {
            drawProximity(viewer, sanctuary, horizontalSpacing, verticalSpacing, minimumDistance, maximumDistance);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to draw Sanctuary graph boundary", exception);
        }
    }

    public boolean isWithinRenderDistance(
        Sanctuary sanctuary,
        String world,
        double x,
        double z,
        double maximumDistance
    ) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(world, "world");
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and greater than zero");
        }
        return boundaryCircles(sanctuary).stream().anyMatch(circle ->
            circle.position().world().equals(world)
                && TerritoryCalculator.distanceToBoundary(circle.position(), circle.radius(), x, z)
                    < maximumDistance
        );
    }

    static int pointCount(double radius, double particleSpacing) {
        validateSpacing(radius, "radius");
        validateSpacing(particleSpacing, "particleSpacing");
        return Math.max(12, (int) Math.ceil((2.0 * Math.PI * radius) / particleSpacing));
    }

    static boolean isWithinProximityBand(
        double distance,
        double minimumDistance,
        double maximumDistance
    ) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and zero or greater");
        }
        validateProximityDistances(minimumDistance, maximumDistance);
        return distance > minimumDistance && distance < maximumDistance;
    }

    static double proximityHalfHeight(double horizontalDistance, double maximumDistance) {
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0.0) {
            throw new IllegalArgumentException("horizontalDistance must be finite and zero or greater");
        }
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and greater than zero");
        }
        if (horizontalDistance >= maximumDistance) {
            return 0.0;
        }
        return Math.sqrt(maximumDistance * maximumDistance - horizontalDistance * horizontalDistance);
    }

    private BukkitTask schedule(
        Player viewer,
        List<Sanctuary> sanctuaries,
        double particleSpacing,
        int displaySeconds,
        double maximumRenderDistance
    ) {
        int repetitions = Math.max(1, displaySeconds * 2);
        final int[] remaining = {repetitions};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!viewer.isOnline()) {
                task[0].cancel();
                return;
            }
            for (Sanctuary sanctuary : sanctuaries) {
                try {
                    if (isWithinManualRenderDistance(viewer, sanctuary, maximumRenderDistance)) {
                        drawFullBoundary(viewer, sanctuary, particleSpacing);
                    }
                } catch (SQLException exception) {
                    logger.log(Level.WARNING, "Failed to render Sanctuary graph boundary", exception);
                }
            }
            remaining[0]--;
            if (remaining[0] <= 0) {
                task[0].cancel();
            }
        }, 0L, 10L);
        return task[0];
    }

    private boolean isWithinManualRenderDistance(
        Player viewer,
        Sanctuary sanctuary,
        double maximumRenderDistance
    ) throws SQLException {
        if (Double.isInfinite(maximumRenderDistance)) {
            return true;
        }
        if (anchorTerritoryService != null) {
            return anchors(sanctuary).stream().anyMatch(anchor -> {
                SanctuaryPosition position = anchor.position().orElseThrow();
                return viewer.getWorld().getName().equals(position.world())
                    && TerritoryCalculator.distanceToBoundary(
                        position,
                        anchor.territoryRadius(),
                        viewer.getLocation().getX(),
                        viewer.getLocation().getZ()
                    ) <= maximumRenderDistance;
            });
        }
        SanctuaryPosition position = sanctuary.position().orElseThrow();
        if (!viewer.getWorld().getName().equals(position.world())) {
            return false;
        }
        return TerritoryCalculator.distanceToBoundary(
            position,
            sanctuary.territoryRadius(),
            viewer.getLocation().getX(),
            viewer.getLocation().getZ()
        ) <= maximumRenderDistance;
    }

    private void drawFullBoundary(Player viewer, Sanctuary sanctuary, double particleSpacing)
        throws SQLException {
        List<BoundaryCircle> circles = boundaryCircles(sanctuary);
        Particle particle = boundaryParticle(viewer, sanctuary);
        for (BoundaryCircle circle : circles) {
            SanctuaryPosition position = circle.position();
            if (!viewer.getWorld().getName().equals(position.world())) {
                continue;
            }
            int points = pointCount(circle.radius(), particleSpacing);
            double centerX = position.x() + 0.5;
            double centerZ = position.z() + 0.5;
            double y = position.y() + 1.25;
            for (int index = 0; index < points; index++) {
                double angle = (2.0 * Math.PI * index) / points;
                double x = centerX + Math.cos(angle) * circle.radius();
                double z = centerZ + Math.sin(angle) * circle.radius();
                if (insideAnotherCircle(circle, circles, x, z)) {
                    continue;
                }
                viewer.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void drawProximity(
        Player viewer,
        Sanctuary sanctuary,
        double horizontalSpacing,
        double verticalSpacing,
        double minimumDistance,
        double maximumDistance
    ) throws SQLException {
        List<BoundaryCircle> circles = boundaryCircles(sanctuary);
        Particle particle = boundaryParticle(viewer, sanctuary);
        double viewerX = viewer.getLocation().getX();
        double viewerY = viewer.getLocation().getY() + 1.0;
        double viewerZ = viewer.getLocation().getZ();

        for (BoundaryCircle circle : circles) {
            SanctuaryPosition position = circle.position();
            if (!viewer.getWorld().getName().equals(position.world())) {
                continue;
            }
            int points = pointCount(circle.radius(), horizontalSpacing);
            double centerX = position.x() + 0.5;
            double centerZ = position.z() + 0.5;
            for (int index = 0; index < points; index++) {
                double angle = (2.0 * Math.PI * index) / points;
                double x = centerX + Math.cos(angle) * circle.radius();
                double z = centerZ + Math.sin(angle) * circle.radius();
                if (insideAnotherCircle(circle, circles, x, z)) {
                    continue;
                }
                double horizontalDistance = Math.hypot(viewerX - x, viewerZ - z);
                if (horizontalDistance >= maximumDistance) {
                    continue;
                }
                double halfHeight = proximityHalfHeight(horizontalDistance, maximumDistance);
                for (double offset = -halfHeight; offset <= halfHeight; offset += verticalSpacing) {
                    double distance = Math.hypot(horizontalDistance, offset);
                    if (!isWithinProximityBand(distance, minimumDistance, maximumDistance)) {
                        continue;
                    }
                    viewer.spawnParticle(particle, x, viewerY + offset, z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    private List<SanctuaryAnchor> anchors(Sanctuary sanctuary) throws SQLException {
        return anchorTerritoryService == null ? List.of() : anchorTerritoryService.activeAnchors(sanctuary.id());
    }

    private List<BoundaryCircle> boundaryCircles(Sanctuary sanctuary) throws SQLException {
        if (anchorTerritoryService == null) {
            return List.of(new BoundaryCircle(
                sanctuary.id(),
                sanctuary.position().orElseThrow(),
                sanctuary.territoryRadius()
            ));
        }
        return anchors(sanctuary).stream()
            .map(anchor -> new BoundaryCircle(
                anchor.id(),
                anchor.position().orElseThrow(),
                anchor.territoryRadius()
            ))
            .toList();
    }

    private static boolean insideAnotherCircle(
        BoundaryCircle source,
        List<BoundaryCircle> circles,
        double x,
        double z
    ) {
        for (BoundaryCircle other : circles) {
            if (other.id().equals(source.id()) || !other.position().world().equals(source.position().world())) {
                continue;
            }
            double dx = x - (other.position().x() + 0.5);
            double dz = z - (other.position().z() + 0.5);
            double radius = Math.max(0.0, other.radius() - UNION_EPSILON);
            if (dx * dx + dz * dz < radius * radius) {
                return true;
            }
        }
        return false;
    }

    private Particle boundaryParticle(Player viewer, Sanctuary sanctuary) {
        try {
            SanctuaryRelationship relationship = securityService.relationship(sanctuary, viewer.getUniqueId());
            if (relationship == SanctuaryRelationship.OWNER) {
                return ownerParticle.get();
            }
            if (relationship == SanctuaryRelationship.TRUSTED) {
                return trustedParticle.get();
            }
            if (relationship == SanctuaryRelationship.BLACKLISTED) {
                return hostileParticle.get();
            }
            if (securityService.mode(sanctuary) == SanctuarySecurityMode.LOCKDOWN) {
                return hostileParticle.get();
            }
            return neutralParticle.get();
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to resolve Sanctuary boundary relationship", exception);
            return neutralParticle.get();
        }
    }

    private void validateManualArguments(Sanctuary sanctuary, double particleSpacing, int displaySeconds) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        if (anchorTerritoryService == null && sanctuary.position().isEmpty()) {
            throw new IllegalArgumentException("Sanctuary must be active to show its boundary");
        }
        if (anchorTerritoryService != null) {
            try {
                if (anchors(sanctuary).isEmpty()) {
                    throw new IllegalArgumentException("Sanctuary must have an active anchor to show its boundary");
                }
            } catch (SQLException exception) {
                throw new IllegalArgumentException("Sanctuary anchors could not be loaded", exception);
            }
        }
        validateSpacing(particleSpacing, "particleSpacing");
        if (displaySeconds < 1) {
            throw new IllegalArgumentException("displaySeconds must be at least 1");
        }
    }

    private static void validateProximityDistances(double minimumDistance, double maximumDistance) {
        if (!Double.isFinite(minimumDistance) || minimumDistance < 0.0) {
            throw new IllegalArgumentException("minimumDistance must be finite and zero or greater");
        }
        if (!Double.isFinite(maximumDistance) || maximumDistance <= minimumDistance) {
            throw new IllegalArgumentException("maximumDistance must be finite and greater than minimumDistance");
        }
    }

    private static void validateSpacing(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }

    private record BoundaryCircle(java.util.UUID id, SanctuaryPosition position, double radius) {}
}
