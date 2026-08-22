package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import java.util.Objects;

public final class TerritoryCalculator {
    private TerritoryCalculator() {
    }

    public static double radiusForArea(double area) {
        if (!Double.isFinite(area) || area <= 0.0) {
            throw new IllegalArgumentException("area must be finite and greater than zero");
        }
        return Math.sqrt(area / Math.PI);
    }

    public static boolean contains(
        SanctuaryPosition center,
        double area,
        String world,
        double x,
        double z
    ) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(world, "world");
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
        if (!center.world().equals(world)) {
            return false;
        }

        double radius = radiusForArea(area);
        double centerX = center.x() + 0.5;
        double centerZ = center.z() + 0.5;
        double deltaX = x - centerX;
        double deltaZ = z - centerZ;
        return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
    }

    public static double horizontalDistance(
        SanctuaryPosition first,
        SanctuaryPosition second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.world().equals(second.world())) {
            return Double.POSITIVE_INFINITY;
        }

        return Math.hypot(
            (double) first.x() - second.x(),
            (double) first.z() - second.z()
        );
    }

    public static double minimumAnchorDistance(double maximumRadius, double margin) {
        if (!Double.isFinite(maximumRadius) || maximumRadius <= 0.0) {
            throw new IllegalArgumentException(
                "maximumRadius must be finite and greater than zero"
            );
        }
        if (!Double.isFinite(margin) || margin < 0.0) {
            throw new IllegalArgumentException("margin must be finite and zero or greater");
        }
        return 2.0 * maximumRadius + margin;
    }
}
