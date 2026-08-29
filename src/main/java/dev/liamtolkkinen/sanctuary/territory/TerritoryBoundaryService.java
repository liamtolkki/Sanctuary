package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.security.SanctuaryRelationship;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityService;
import dev.liamtolkkinen.sanctuary.security.SanctuaryThreat;
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
        this(
            plugin,
            securityService,
            null,
            ownerParticle,
            trustedParticle,
            neutralParticle,
            hostileParticle,
            logger
        );
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

    public BukkitTask show(
        Player viewer,
        Sanctuary sanctuary,
        double particleSpacing,
        int displaySeconds
    ) {
        Objects.requireNonNull(viewer, "viewer");
        validateManualArguments(sanctuary, particleSpacing, displaySeconds);
        return schedule(
            viewer,
            List.of(sanctuary),
            particleSpacing,
            displaySeconds,
            Double.POSITIVE_INFINITY
        );
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
            throw new IllegalArgumentException(
                "maximumRenderDistance must be finite and greater than zero"
            );
        }
        sanctuaries.forEach(value ->
            validateManualArguments(value, particleSpacing, displaySeconds)
        );
        return schedule(
            viewer,
            List.copyOf(sanctuaries),
            particleSpacing,
            displaySeconds,
            maximumRenderDistance
        );
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
            drawProximity(
                viewer,
                sanctuary,
                horizontalSpacing,
                verticalSpacing,
                minimumDistance,
                maximumDistance
            );
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Failed to draw Sanctuary graph boundary", exception);
        }
    }

    public boolean isWithinRenderDistance(
        Sanctuary sanctuary,
        String world,
        double x,
        double y,
        double z,
        double maximumDistance
    ) throws SQLException {
        Objects.requireNonNull(sanctuary, "sanctuary");
        Objects.requireNonNull(world, "world");
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0) {
            throw new IllegalArgumentException(
                "maximumDistance must be finite and greater than zero"
            );
        }
        return boundaryEllipsoids(sanctuary).stream().anyMatch(ellipsoid -> {
            if (!ellipsoid.position().world().equals(world)) {
                return false;
            }
            double scaledDistance = TerritoryCalculator.scaledDistance(
                ellipsoid.position(),
                x,
                y,
                z
            );
            double conservativeDistanceToSurface = Math.abs(
                scaledDistance - ellipsoid.radius()
            ) * TerritoryCalculator.VERTICAL_RADIUS_SCALE;
            return conservativeDistanceToSurface < maximumDistance;
        });
    }

    public boolean isWithinRenderDistance(
        Sanctuary sanctuary,
        String world,
        double x,
        double z,
        double maximumDistance
    ) throws SQLException {
        List<BoundaryEllipsoid> ellipsoids = boundaryEllipsoids(sanctuary);
        if (ellipsoids.isEmpty()) {
            return false;
        }
        return isWithinRenderDistance(
            sanctuary,
            world,
            x,
            ellipsoids.get(0).position().y() + 0.5,
            z,
            maximumDistance
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
            throw new IllegalArgumentException(
                "horizontalDistance must be finite and zero or greater"
            );
        }
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0) {
            throw new IllegalArgumentException(
                "maximumDistance must be finite and greater than zero"
            );
        }
        if (horizontalDistance >= maximumDistance) {
            return 0.0;
        }
        return Math.sqrt(
            maximumDistance * maximumDistance - horizontalDistance * horizontalDistance
        );
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
                    if (isWithinManualRenderDistance(
                        viewer,
                        sanctuary,
                        maximumRenderDistance
                    )) {
                        drawFullBoundary(viewer, sanctuary, particleSpacing);
                    }
                } catch (SQLException exception) {
                    logger.log(
                        Level.WARNING,
                        "Failed to render Sanctuary graph boundary",
                        exception
                    );
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
        return isWithinRenderDistance(
            sanctuary,
            viewer.getWorld().getName(),
            viewer.getLocation().getX(),
            viewer.getLocation().getY(),
            viewer.getLocation().getZ(),
            maximumRenderDistance
        );
    }

    private void drawFullBoundary(
        Player viewer,
        Sanctuary sanctuary,
        double particleSpacing
    ) throws SQLException {
        List<BoundaryEllipsoid> ellipsoids = boundaryEllipsoids(sanctuary);
        Particle particle = boundaryParticle(viewer, sanctuary);
        for (BoundaryEllipsoid ellipsoid : ellipsoids) {
            drawEllipsoidSurface(
                viewer,
                particle,
                ellipsoid,
                ellipsoids,
                particleSpacing,
                particleSpacing,
                0.0,
                Double.POSITIVE_INFINITY
            );
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
        List<BoundaryEllipsoid> ellipsoids = boundaryEllipsoids(sanctuary);
        Particle particle = boundaryParticle(viewer, sanctuary);
        for (BoundaryEllipsoid ellipsoid : ellipsoids) {
            drawEllipsoidSurface(
                viewer,
                particle,
                ellipsoid,
                ellipsoids,
                horizontalSpacing,
                verticalSpacing,
                minimumDistance,
                maximumDistance
            );
        }
    }

    private void drawEllipsoidSurface(
        Player viewer,
        Particle particle,
        BoundaryEllipsoid source,
        List<BoundaryEllipsoid> ellipsoids,
        double horizontalSpacing,
        double verticalSpacing,
        double minimumDistance,
        double maximumDistance
    ) {
        SanctuaryPosition position = source.position();
        if (!viewer.getWorld().getName().equals(position.world())) {
            return;
        }

        double centerX = position.x() + 0.5;
        double centerY = position.y() + 0.5;
        double centerZ = position.z() + 0.5;
        double verticalRadius = TerritoryCalculator.verticalRadius(source.radius());
        int latitudeSteps = Math.max(
            2,
            (int) Math.ceil((verticalRadius * 2.0) / verticalSpacing)
        );

        for (int latitudeIndex = 0; latitudeIndex <= latitudeSteps; latitudeIndex++) {
            double normalizedY = -1.0 + (2.0 * latitudeIndex / latitudeSteps);
            double yOffset = normalizedY * verticalRadius;
            double ringRadius = source.radius()
                * Math.sqrt(Math.max(0.0, 1.0 - normalizedY * normalizedY));
            double y = centerY + yOffset;

            if (ringRadius < 1.0e-6) {
                spawnSurfacePointIfVisible(
                    viewer,
                    particle,
                    source,
                    ellipsoids,
                    centerX,
                    y,
                    centerZ,
                    minimumDistance,
                    maximumDistance
                );
                continue;
            }

            int points = pointCount(ringRadius, horizontalSpacing);
            for (int index = 0; index < points; index++) {
                double angle = (2.0 * Math.PI * index) / points;
                double x = centerX + Math.cos(angle) * ringRadius;
                double z = centerZ + Math.sin(angle) * ringRadius;
                spawnSurfacePointIfVisible(
                    viewer,
                    particle,
                    source,
                    ellipsoids,
                    x,
                    y,
                    z,
                    minimumDistance,
                    maximumDistance
                );
            }
        }
    }

    private void spawnSurfacePointIfVisible(
        Player viewer,
        Particle particle,
        BoundaryEllipsoid source,
        List<BoundaryEllipsoid> ellipsoids,
        double x,
        double y,
        double z,
        double minimumDistance,
        double maximumDistance
    ) {
        if (insideAnotherEllipsoid(source, ellipsoids, x, y, z)) {
            return;
        }
        if (!Double.isInfinite(maximumDistance)) {
            double dx = viewer.getLocation().getX() - x;
            double dy = (viewer.getLocation().getY() + 1.0) - y;
            double dz = viewer.getLocation().getZ() - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!isWithinProximityBand(distance, minimumDistance, maximumDistance)) {
                return;
            }
        }
        viewer.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private List<SanctuaryAnchor> anchors(Sanctuary sanctuary) throws SQLException {
        return anchorTerritoryService == null
            ? List.of()
            : anchorTerritoryService.activeAnchors(sanctuary.id());
    }

    private List<BoundaryEllipsoid> boundaryEllipsoids(Sanctuary sanctuary)
        throws SQLException {
        if (anchorTerritoryService == null) {
            return List.of(new BoundaryEllipsoid(
                sanctuary.id(),
                sanctuary.position().orElseThrow(),
                sanctuary.territoryRadius()
            ));
        }
        return anchors(sanctuary).stream()
            .map(anchor -> new BoundaryEllipsoid(
                anchor.id(),
                anchor.position().orElseThrow(),
                anchor.territoryRadius()
            ))
            .toList();
    }

    private static boolean insideAnotherEllipsoid(
        BoundaryEllipsoid source,
        List<BoundaryEllipsoid> ellipsoids,
        double x,
        double y,
        double z
    ) {
        for (BoundaryEllipsoid other : ellipsoids) {
            if (other.id().equals(source.id())
                || !other.position().world().equals(source.position().world())) {
                continue;
            }
            double radius = Math.max(0.0, other.radius() - UNION_EPSILON);
            if (radius <= 0.0) {
                continue;
            }
            if (TerritoryCalculator.scaledDistanceSquared(
                other.position(),
                x,
                y,
                z
            ) < radius * radius) {
                return true;
            }
        }
        return false;
    }

    private Particle boundaryParticle(Player viewer, Sanctuary sanctuary) {
        try {
            SanctuaryThreat threat = securityService.threat(
                sanctuary,
                viewer.getUniqueId()
            );
            if (threat == SanctuaryThreat.HOSTILE) {
                return hostileParticle.get();
            }

            SanctuaryRelationship relationship = securityService.relationship(
                sanctuary,
                viewer.getUniqueId()
            );
            if (relationship == SanctuaryRelationship.OWNER) {
                return ownerParticle.get();
            }
            if (relationship == SanctuaryRelationship.TRUSTED) {
                return trustedParticle.get();
            }
            return neutralParticle.get();
        } catch (SQLException exception) {
            logger.log(
                Level.WARNING,
                "Failed to resolve Sanctuary boundary threat",
                exception
            );
            return neutralParticle.get();
        }
    }

    private void validateManualArguments(
        Sanctuary sanctuary,
        double particleSpacing,
        int displaySeconds
    ) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        if (anchorTerritoryService == null && sanctuary.position().isEmpty()) {
            throw new IllegalArgumentException(
                "Sanctuary must be active to show its boundary"
            );
        }
        if (anchorTerritoryService != null) {
            try {
                if (anchors(sanctuary).isEmpty()) {
                    throw new IllegalArgumentException(
                        "Sanctuary must have an active anchor to show its boundary"
                    );
                }
            } catch (SQLException exception) {
                throw new IllegalArgumentException(
                    "Sanctuary anchors could not be loaded",
                    exception
                );
            }
        }
        validateSpacing(particleSpacing, "particleSpacing");
        if (displaySeconds < 1) {
            throw new IllegalArgumentException("displaySeconds must be at least 1");
        }
    }

    private static void validateProximityDistances(
        double minimumDistance,
        double maximumDistance
    ) {
        if (!Double.isFinite(minimumDistance) || minimumDistance < 0.0) {
            throw new IllegalArgumentException(
                "minimumDistance must be finite and zero or greater"
            );
        }
        if (!Double.isFinite(maximumDistance) || maximumDistance <= minimumDistance) {
            throw new IllegalArgumentException(
                "maximumDistance must be finite and greater than minimumDistance"
            );
        }
    }

    private static void validateSpacing(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                name + " must be finite and greater than zero"
            );
        }
    }

    private record BoundaryEllipsoid(
        java.util.UUID id,
        SanctuaryPosition position,
        double radius
    ) {
    }
}
