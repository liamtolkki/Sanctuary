package dev.liamtolkkinen.sanctuary.territory;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TerritoryUnionCalculator {
    private static final double EPSILON = 1.0e-9;
    private static final double FULL_CIRCLE = 2.0 * Math.PI;

    private TerritoryUnionCalculator() {
    }

    public static double area(List<SanctuaryAnchor> anchors) {
        Objects.requireNonNull(anchors, "anchors");
        return areaOfCircles(anchors.stream()
            .filter(anchor -> anchor.state() == SanctuaryState.ACTIVE)
            .filter(anchor -> anchor.position().isPresent())
            .map(anchor -> circle(anchor.position().orElseThrow(), anchor.territoryRadius()))
            .toList());
    }

    static double areaOfCircles(List<Circle> circles) {
        Objects.requireNonNull(circles, "circles");
        double area = 0.0;
        for (int index = 0; index < circles.size(); index++) {
            Circle circle = circles.get(index);
            validate(circle);
            List<Interval> covered = coveredIntervals(index, circles);
            if (covered.size() == 1 && covered.getFirst().start() <= 0.0
                && covered.getFirst().end() >= FULL_CIRCLE) {
                continue;
            }
            double start = 0.0;
            for (Interval interval : covered) {
                area += arcArea(circle, start, interval.start());
                start = Math.max(start, interval.end());
            }
            area += arcArea(circle, start, FULL_CIRCLE);
        }
        return Math.max(0.0, area);
    }

    private static List<Interval> coveredIntervals(int sourceIndex, List<Circle> circles) {
        Circle source = circles.get(sourceIndex);
        List<Interval> intervals = new ArrayList<>();
        for (int index = 0; index < circles.size(); index++) {
            if (index == sourceIndex) continue;
            Circle other = circles.get(index);
            validate(other);
            if (!source.world().equals(other.world())) continue;

            double dx = other.x() - source.x();
            double dz = other.z() - source.z();
            double distance = Math.hypot(dx, dz);
            if (distance + source.radius() <= other.radius() + EPSILON) {
                if (distance > EPSILON || source.radius() < other.radius() - EPSILON || sourceIndex > index) {
                    return List.of(new Interval(0.0, FULL_CIRCLE));
                }
                continue;
            }
            if (distance >= source.radius() + other.radius() - EPSILON
                || distance <= Math.abs(source.radius() - other.radius()) + EPSILON) {
                continue;
            }

            double center = normalize(Math.atan2(dz, dx));
            double halfWidth = Math.acos(clamp(
                (distance * distance + source.radius() * source.radius() - other.radius() * other.radius())
                    / (2.0 * distance * source.radius())
            ));
            addWrapped(intervals, center - halfWidth, center + halfWidth);
        }
        return merge(intervals);
    }

    private static void addWrapped(List<Interval> intervals, double start, double end) {
        start = normalize(start);
        end = normalize(end);
        if (start <= end) {
            intervals.add(new Interval(start, end));
        } else {
            intervals.add(new Interval(0.0, end));
            intervals.add(new Interval(start, FULL_CIRCLE));
        }
    }

    private static List<Interval> merge(List<Interval> intervals) {
        if (intervals.isEmpty()) return List.of();
        intervals.sort(Comparator.comparingDouble(Interval::start));
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : intervals) {
            if (merged.isEmpty() || interval.start() > merged.getLast().end() + EPSILON) {
                merged.add(interval);
            } else {
                Interval previous = merged.removeLast();
                merged.add(new Interval(previous.start(), Math.max(previous.end(), interval.end())));
            }
        }
        return merged;
    }

    private static double arcArea(Circle circle, double start, double end) {
        if (end <= start + EPSILON) return 0.0;
        double radius = circle.radius();
        return 0.5 * (
            radius * radius * (end - start)
                + radius * circle.x() * (Math.sin(end) - Math.sin(start))
                + radius * circle.z() * (Math.cos(start) - Math.cos(end))
        );
    }

    private static Circle circle(SanctuaryPosition position, double radius) {
        return new Circle(position.world(), position.x() + 0.5, position.z() + 0.5, radius);
    }

    private static void validate(Circle circle) {
        Objects.requireNonNull(circle, "circle");
        Objects.requireNonNull(circle.world(), "circle.world");
        if (!Double.isFinite(circle.x()) || !Double.isFinite(circle.z())
            || !Double.isFinite(circle.radius()) || circle.radius() <= 0.0) {
            throw new IllegalArgumentException("Circle coordinates and radius must be finite, with radius greater than zero");
        }
    }

    private static double normalize(double angle) {
        double normalized = angle % FULL_CIRCLE;
        return normalized < 0.0 ? normalized + FULL_CIRCLE : normalized;
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    record Circle(String world, double x, double z, double radius) {}
    private record Interval(double start, double end) {}
}
