package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import java.util.List;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TerritoryBoundaryService {
    private final JavaPlugin plugin;

    public TerritoryBoundaryService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        double triggerDistance
    ) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(sanctuary, "sanctuary");
        validateSpacing(horizontalSpacing, "horizontalSpacing");
        validateSpacing(verticalSpacing, "verticalSpacing");
        if (!Double.isFinite(triggerDistance) || triggerDistance <= 0.0) {
            throw new IllegalArgumentException("triggerDistance must be finite and greater than zero");
        }
        drawProximity(viewer, sanctuary, horizontalSpacing, verticalSpacing, triggerDistance);
    }

    static int pointCount(double radius, double particleSpacing) {
        validateSpacing(radius, "radius");
        validateSpacing(particleSpacing, "particleSpacing");
        return Math.max(12, (int) Math.ceil((2.0 * Math.PI * radius) / particleSpacing));
    }

    static double proximityHalfHeight(double horizontalDistance, double triggerDistance) {
        if (!Double.isFinite(horizontalDistance) || horizontalDistance < 0.0) {
            throw new IllegalArgumentException("horizontalDistance must be finite and zero or greater");
        }
        if (!Double.isFinite(triggerDistance) || triggerDistance <= 0.0) {
            throw new IllegalArgumentException("triggerDistance must be finite and greater than zero");
        }
        if (horizontalDistance >= triggerDistance) {
            return 0.0;
        }
        return Math.sqrt(triggerDistance * triggerDistance - horizontalDistance * horizontalDistance);
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

    private static void drawFullBoundary(Player viewer, Sanctuary sanctuary, double particleSpacing) {
        SanctuaryPosition position = sanctuary.position().orElseThrow();
        if (!viewer.getWorld().getName().equals(position.world())) {
            return;
        }
        double radius = sanctuary.territoryRadius();
        int points = pointCount(radius, particleSpacing);
        double centerX = position.x() + 0.5;
        double centerZ = position.z() + 0.5;
        double y = position.y() + 1.25;

        for (int index = 0; index < points; index++) {
            double angle = (2.0 * Math.PI * index) / points;
            viewer.spawnParticle(
                Particle.END_ROD,
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

    private static void drawProximity(
        Player viewer,
        Sanctuary sanctuary,
        double horizontalSpacing,
        double verticalSpacing,
        double triggerDistance
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

        for (int index = 0; index < points; index++) {
            double angle = (2.0 * Math.PI * index) / points;
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            double horizontalDistance = Math.hypot(viewerX - x, viewerZ - z);
            double halfHeight = proximityHalfHeight(horizontalDistance, triggerDistance);
            if (halfHeight <= 0.0) {
                continue;
            }

            for (double offset = -halfHeight; offset <= halfHeight; offset += verticalSpacing) {
                viewer.spawnParticle(Particle.END_ROD, x, viewerY + offset, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
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

    private static void validateSpacing(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }
}
