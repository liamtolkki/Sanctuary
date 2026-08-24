package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Calculates the continuous X/Z area covered by active Sanctuary anchor territory circles. */
public final class TerritoryAreaCalculator {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double EPSILON = 1.0e-9;

    private TerritoryAreaCalculator() {
    }

    /**
     * Returns the union area, in square blocks, of the anchors' current territory circles.
     *
     * <p>Only ACTIVE anchors with a physical position participate. Each circle uses the
     * anchor's persisted {@link SanctuaryAnchor#territoryRadius()} exactly as-is. Worlds are
     * independent planes, so equal/overlapping coordinates in different worlds are summed
     * rather than treated as overlap.</p>
     */
    public static double currentUnionArea(Collection<SanctuaryAnchor> anchors) {
        Objects.requireNonNull(anchors, "anchors");

        Map<String, List<Circle>> circlesByWorld = new LinkedHashMap<>();
        for (SanctuaryAnchor anchor : anchors) {
            Objects.requireNonNull(anchor, "anchor");
            if (anchor.state() != SanctuaryState.ACTIVE || anchor.position().isEmpty()) {
                continue;
            }

            SanctuaryPosition position = anchor.position().orElseThrow();
            circlesByWorld.computeIfAbsent(position.world(), ignored -> new ArrayList<>())
                .add(new Circle(
                    position.x() + 0.5,
                    position.z() + 0.5,
                    anchor.territoryRadius()
                ));
        }

        double area = 0.0;
        for (List<Circle> circles : circlesByWorld.values()) {
            area += unionArea(circles);
        }
        return Math.max(0.0, area);
    }

    private static double unionArea(List<Circle> circles) {
        if (circles.isEmpty()) {
            return 0.0;
        }

        // Shift coordinates near the origin to reduce cancellation in the line integral when
        // anchors are placed at very large Minecraft coordinates. Area is translation invariant.
        double originX = circles.getFirst().x();
        double originZ = circles.getFirst().z();
        List<Circle> shifted = circles.stream()
            .map(circle -> new Circle(circle.x() - originX, circle.z() - originZ, circle.radius()))
            .toList();

        double area = 0.0;
        for (int index = 0; index < shifted.size(); index++) {
            Circle circle = shifted.get(index);
            List<Interval> covered = new ArrayList<>();
            boolean fullyCovered = false;

            for (int otherIndex = 0; otherIndex < shifted.size(); otherIndex++) {
                if (otherIndex == index) {
                    continue;
                }

                Circle other = shifted.get(otherIndex);
                double dx = other.x() - circle.x();
                double dz = other.z() - circle.z();
                double distance = Math.hypot(dx, dz);

                // Equal duplicate circles must contribute exactly once. For strict containment,
                // the smaller circle contributes no exposed perimeter and therefore no area.
                if (distance <= EPSILON && Math.abs(circle.radius() - other.radius()) <= EPSILON) {
                    if (otherIndex < index) {
                        fullyCovered = true;
                        break;
                    }
                    continue;
                }
                if (distance + circle.radius() <= other.radius() + EPSILON) {
                    fullyCovered = true;
                    break;
                }

                // Disjoint/tangent circles and circles fully inside this one do not cover any
                // positive-length portion of this circle's perimeter.
                if (distance >= circle.radius() + other.radius() - EPSILON
                    || distance + other.radius() <= circle.radius() + EPSILON) {
                    continue;
                }

                double centerAngle = Math.atan2(dz, dx);
                double cosine = (
                    circle.radius() * circle.radius()
                        + distance * distance
                        - other.radius() * other.radius()
                ) / (2.0 * circle.radius() * distance);
                cosine = Math.max(-1.0, Math.min(1.0, cosine));
                double halfWidth = Math.acos(cosine);
                addNormalizedInterval(covered, centerAngle - halfWidth, centerAngle + halfWidth);
            }

            if (fullyCovered) {
                continue;
            }

            List<Interval> merged = merge(covered);
            double cursor = 0.0;
            for (Interval interval : merged) {
                if (interval.start() > cursor + EPSILON) {
                    area += exposedArcArea(circle, cursor, interval.start());
                }
                cursor = Math.max(cursor, interval.end());
            }
            if (cursor < TWO_PI - EPSILON) {
                area += exposedArcArea(circle, cursor, TWO_PI);
            }
        }
        return area;
    }

    private static void addNormalizedInterval(List<Interval> intervals, double start, double end) {
        while (start < 0.0) {
            start += TWO_PI;
            end += TWO_PI;
        }
        while (start >= TWO_PI) {
            start -= TWO_PI;
            end -= TWO_PI;
        }

        if (end <= TWO_PI) {
            intervals.add(new Interval(start, end));
            return;
        }
        intervals.add(new Interval(start, TWO_PI));
        intervals.add(new Interval(0.0, end - TWO_PI));
    }

    private static List<Interval> merge(List<Interval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }

        List<Interval> sorted = intervals.stream()
            .sorted(Comparator.comparingDouble(Interval::start).thenComparingDouble(Interval::end))
            .toList();
        List<Interval> merged = new ArrayList<>();
        double start = sorted.getFirst().start();
        double end = sorted.getFirst().end();
        for (int index = 1; index < sorted.size(); index++) {
            Interval interval = sorted.get(index);
            if (interval.start() <= end + EPSILON) {
                end = Math.max(end, interval.end());
            } else {
                merged.add(new Interval(start, end));
                start = interval.start();
                end = interval.end();
            }
        }
        merged.add(new Interval(start, end));
        return merged;
    }

    /**
     * Green's-theorem contribution of one exposed counter-clockwise circle arc.
     */
    private static double exposedArcArea(Circle circle, double start, double end) {
        double radius = circle.radius();
        return 0.5 * (
            radius * circle.x() * (Math.sin(end) - Math.sin(start))
                + radius * circle.z() * (Math.cos(start) - Math.cos(end))
                + radius * radius * (end - start)
        );
    }

    private record Circle(double x, double z, double radius) {
    }

    private record Interval(double start, double end) {
    }
}
