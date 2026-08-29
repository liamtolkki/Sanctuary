package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import java.util.Objects;

/** Shared geometry for Sanctuary territory. */
public final class TerritoryCalculator {
    /**
     * Vertical semi-axis as a fraction of the stored horizontal territory radius.
     * This produces x^2 + 2.25y^2 + z^2 <= r^2.
     */
    public static final double VERTICAL_RADIUS_SCALE = 2.0 / 3.0;
    private static final double VERTICAL_DISTANCE_WEIGHT =
        1.0 / (VERTICAL_RADIUS_SCALE * VERTICAL_RADIUS_SCALE);

    private TerritoryCalculator() {
    }

    /** Actual three-dimensional Sanctuary defensive volume. */
    public static boolean contains(
        SanctuaryPosition center,
        double radius,
        String world,
        double x,
        double y,
        double z
    ) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(world, "world");
        validateRadius(radius);
        validateCoordinates(x, y, z);
        if (!center.world().equals(world)) {
            return false;
        }
        return scaledDistanceSquared(center, x, y, z) <= radius * radius;
    }

    /**
     * Horizontal footprint check retained for spacing, map/area calculations, and compatibility.
     * Gameplay presence/effects/protections should use the six-argument 3D overload above.
     */
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
        double deltaX = x - (center.x() + 0.5);
        double deltaZ = z - (center.z() + 0.5);
        return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
    }

    /** Scaled radius-space distance used for nested effect tiers inside the ellipsoid. */
    public static double scaledDistance(
        SanctuaryPosition center,
        double x,
        double y,
        double z
    ) {
        return Math.sqrt(scaledDistanceSquared(center, x, y, z));
    }

    public static double scaledDistanceSquared(
        SanctuaryPosition center,
        double x,
        double y,
        double z
    ) {
        Objects.requireNonNull(center, "center");
        validateCoordinates(x, y, z);
        double deltaX = x - (center.x() + 0.5);
        double deltaY = y - (center.y() + 0.5);
        double deltaZ = z - (center.z() + 0.5);
        return deltaX * deltaX
            + deltaY * deltaY * VERTICAL_DISTANCE_WEIGHT
            + deltaZ * deltaZ;
    }

    public static double verticalRadius(double horizontalRadius) {
        validateRadius(horizontalRadius);
        return horizontalRadius * VERTICAL_RADIUS_SCALE;
    }

    /** Horizontal radius of an ellipsoid latitude ring at the supplied Y offset. */
    public static double horizontalRadiusAtVerticalOffset(
        double radius,
        double verticalOffset
    ) {
        validateRadius(radius);
        if (!Double.isFinite(verticalOffset)) {
            throw new IllegalArgumentException("verticalOffset must be finite");
        }
        double normalized = verticalOffset / verticalRadius(radius);
        if (Math.abs(normalized) >= 1.0) {
            return 0.0;
        }
        return radius * Math.sqrt(Math.max(0.0, 1.0 - normalized * normalized));
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

    /** Horizontal-footprint boundary distance retained for placement/render compatibility. */
    public static double distanceToBoundary(
        SanctuaryPosition center,
        double radius,
        double x,
        double z
    ) {
        Objects.requireNonNull(center, "center");
        validateRadius(radius);
        double centerDistance = Math.hypot(
            x - (center.x() + 0.5),
            z - (center.z() + 0.5)
        );
        return Math.abs(centerDistance - radius);
    }

    public static double minimumAnchorDistance(double maximumRadius, double margin) {
        validateRadius(maximumRadius);
        if (!Double.isFinite(margin) || margin < 0.0) {
            throw new IllegalArgumentException(
                "margin must be finite and zero or greater"
            );
        }
        return 2.0 * maximumRadius + margin;
    }

    public static double migratedRadiusForArea(double area) {
        if (!Double.isFinite(area) || area <= 0.0) {
            throw new IllegalArgumentException(
                "area must be finite and greater than zero"
            );
        }
        return Math.sqrt(area / Math.PI);
    }

    private static void validateCoordinates(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
    }

    private static void validateRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException(
                "radius must be finite and greater than zero"
            );
        }
    }
}
