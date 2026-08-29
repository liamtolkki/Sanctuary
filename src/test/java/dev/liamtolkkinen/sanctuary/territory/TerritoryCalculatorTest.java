package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import org.junit.jupiter.api.Test;

class TerritoryCalculatorTest {
    @Test
    void legacyAreaConvertsToEquivalentRadius() {
        assertEquals(10.0, TerritoryCalculator.migratedRadiusForArea(Math.PI * 100.0), 0.000001);
    }

    @Test
    void threeDimensionalContainmentUsesFlattenedEllipsoid() {
        SanctuaryPosition center = new SanctuaryPosition("world", 0, 64, 0);
        double radius = 10.0;
        double centerY = 64.5;
        double verticalRadius = TerritoryCalculator.verticalRadius(radius);

        assertEquals(20.0 / 3.0, verticalRadius, 0.000001);
        assertTrue(TerritoryCalculator.contains(center, radius, "world", 10.5, centerY, 0.5));
        assertFalse(TerritoryCalculator.contains(center, radius, "world", 10.51, centerY, 0.5));
        assertTrue(TerritoryCalculator.contains(
            center,
            radius,
            "world",
            0.5,
            centerY + verticalRadius,
            0.5
        ));
        assertFalse(TerritoryCalculator.contains(
            center,
            radius,
            "world",
            0.5,
            centerY + verticalRadius + 0.01,
            0.5
        ));
        assertTrue(TerritoryCalculator.contains(center, radius, "world", 6.5, centerY + 4.0, 0.5));
        assertFalse(TerritoryCalculator.contains(center, radius, "world_nether", 0.5, centerY, 0.5));
    }

    @Test
    void horizontalCompatibilityContainmentStillUsesStoredRadius() {
        SanctuaryPosition center = new SanctuaryPosition("world", 0, 64, 0);
        double radius = 10.0;

        assertTrue(TerritoryCalculator.contains(center, radius, "world", 10.5, 0.5));
        assertTrue(TerritoryCalculator.contains(center, radius, "world", 6.5, 8.5));
        assertFalse(TerritoryCalculator.contains(center, radius, "world", 10.51, 0.5));
    }

    @Test
    void tierFiveVerticalRadiusIsSixtyFourBlocks() {
        assertEquals(64.0, TerritoryCalculator.verticalRadius(96.0), 0.000001);
    }

    @Test
    void spacingUsesTwoMaximumRadiiPlusMargin() {
        assertEquals(208.0, TerritoryCalculator.minimumAnchorDistance(96.0, 16.0));
    }
}
