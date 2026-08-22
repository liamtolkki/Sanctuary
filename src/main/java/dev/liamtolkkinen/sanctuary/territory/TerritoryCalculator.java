package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import java.util.Objects;

public final class TerritoryCalculator {
    private TerritoryCalculator() {
    }

    public static boolean contains(
        SanctuaryPosition center,
        double radius,
        String world,
        double x,
        double z
    ) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(world, "world");
        validateRadius(radius);
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
        if (!center.world().equals(world)) {
            return false;
        }

        double centerX = center.x() + 0.5;
        double centerZ = center.z() + 0.5;
        double deltaX = x - centerX;
        double deltaZ = z - centerZ;
        return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
    }

    public static double horizontalDistance(SanctuaryPosition first, SanctuaryPosition second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.world().equals(second.world())) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot((double) first.x() - second.x(), (double) first.z() - second.z());
    }

    public static double distanceToBoundary(
        SanctuaryPosition center,
        double radius,
        double x,
        double z
    ) {
        Objects.requireNonNull(center, "center");
        validateRadius(radius);
        double centerDistance = Math.hypot(x - (center.x() + 0.5), z - (center.z() + 0.5));
        return Math.abs(centerDistance - radius);
    }

    public static double minimumAnchorDistance(double maximumRadius, double margin) {
        validateRadius(maximumRadius);
        if (!Double.isFinite(margin) || margin < 0.0) {
            throw new IllegalArgumentException("margin must be finite and zero or greater");
        }
        return 2.0 * maximumRadius + margin;
    }

    public static double migratedRadiusForArea(double area) {
        if (!Double.isFinite(area) || area <= 0.0) {
            throw new IllegalArgumentException("area must be finite and greater than zero");
        }
        return Math.sqrt(area / Math.PI);
    }

    private static void validateRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and greater than zero");
        }
    }
}
