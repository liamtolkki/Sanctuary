package dev.liamtolkkinen.sanctuary.territory;

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
    private final JavaPlugin plugin;
    private final SanctuarySecurityService securityService;
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
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.securityService = Objects.requireNonNull(securityService, "securityService");
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
        drawProximity(
            viewer,
            sanctuary,
            horizontalSpacing,
            verticalSpacing,
            minimumDistance,
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
                if (isWithinManualRenderDistance(viewer, sanctuary, maximumRenderDistance)) {
                    drawFullBoundary(viewer, sanctuary, particleSpacing);
                }
            }
            remaining[0]--;
            if (remaining[0] <= 0) {
                task[0].cancel();
            }
        }, 0L, 10L);
        return task[0];
    }

    private static boolean isWithinManualRenderDistance(
        Player viewer,
        Sanctuary sanctuary,
        double maximumRenderDistance
    ) {
        if (Double.isInfinite(maximumRenderDistance)) {
            return true;
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

    private void drawFullBoundary(Player viewer, Sanctuary sanctuary, double particleSpacing) {
        SanctuaryPosition position = sanctuary.position().orElseThrow();
        if (!viewer.getWorld().getName().equals(position.world())) {
            return;
        }
        double radius = sanctuary.territoryRadius();
        int points = pointCount(radius, particleSpacing);
        double centerX = position.x() + 0.5;
        double centerZ = position.z() + 0.5;
        double y = position.y() + 1.25;
        Particle particle = boundaryParticle(viewer, sanctuary);

        for (int index = 0; index < points; index++) {
            double angle = (2.0 * Math.PI * index) / points;
            viewer.spawnParticle(
                particle,
                centerX + Math.cos(angle) * radius,
                y,
                centerZ + Math.sin(angle) * radius,
                1,
                0.0,
                0.0,
                0.0,
                0.0
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
    ) {
        SanctuaryPosition position = sanctuary.position().orElseThrow();
        if (!viewer.getWorld().getName().equals(position.world())) {
            return;
        }

        double radius = sanctuary.territoryRadius();
        int points = pointCount(radius, horizontalSpacing);
        double centerX = position.x() + 0.5;
        double centerZ = position.z() + 0.5;
        double viewerX = viewer.getLocation().getX();
        double viewerY = viewer.getLocation().getY() + 1.0;
        double viewerZ = viewer.getLocation().getZ();
        Particle particle = boundaryParticle(viewer, sanctuary);

        for (int index = 0; index < points; index++) {
            double angle = (2.0 * Math.PI * index) / points;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
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

                viewer.spawnParticle(
                    particle,
                    x,
                    viewerY + offset,
                    z,
                    1,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                );
            }
        }
    }

    private Particle boundaryParticle(Player viewer, Sanctuary sanctuary) {
        try {
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

    private static void validateManualArguments(Sanctuary sanctuary, double particleSpacing, int displaySeconds) {
        Objects.requireNonNull(sanctuary, "sanctuary");
        if (sanctuary.position().isEmpty()) {
            throw new IllegalArgumentException("Sanctuary must be active to show its boundary");
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
}
