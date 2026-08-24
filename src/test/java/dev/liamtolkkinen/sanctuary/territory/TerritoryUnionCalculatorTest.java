package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TerritoryUnionCalculatorTest {
    @Test
    void disjointCirclesAddTheirAreas() {
        assertEquals(2.0 * Math.PI * 100.0, TerritoryUnionCalculator.areaOfCircles(List.of(
            circle("world", 0.0, 0.0, 10.0),
            circle("world", 30.0, 0.0, 10.0)
        )), 0.000001);
    }

    @Test
    void overlappingCirclesCountTheIntersectionOnce() {
        double radius = 10.0;
        double distance = 10.0;
        double intersection = 2.0 * radius * radius * Math.acos(distance / (2.0 * radius))
            - 0.5 * distance * Math.sqrt(4.0 * radius * radius - distance * distance);
        assertEquals(2.0 * Math.PI * radius * radius - intersection,
            TerritoryUnionCalculator.areaOfCircles(List.of(
                circle("world", 0.0, 0.0, radius),
                circle("world", distance, 0.0, radius)
            )), 0.000001);
    }

    @Test
    void containedAndDuplicateCirclesDoNotIncreaseArea() {
        assertEquals(Math.PI * 100.0, TerritoryUnionCalculator.areaOfCircles(List.of(
            circle("world", 0.0, 0.0, 10.0),
            circle("world", 0.0, 0.0, 4.0),
            circle("world", 0.0, 0.0, 10.0)
        )), 0.000001);
    }

    @Test
    void circlesInDifferentWorldsDoNotOverlap() {
        assertEquals(2.0 * Math.PI * 100.0, TerritoryUnionCalculator.areaOfCircles(List.of(
            circle("world", 0.0, 0.0, 10.0),
            circle("world_nether", 0.0, 0.0, 10.0)
        )), 0.000001);
    }

    private static TerritoryUnionCalculator.Circle circle(
        String world, double x, double z, double radius
    ) {
        return new TerritoryUnionCalculator.Circle(world, x, z, radius);
    }
}
