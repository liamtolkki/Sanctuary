package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import org.junit.jupiter.api.Test;

class TerritoryCalculatorTest {
    @Test
    void radiusIsDerivedFromArea() {
        assertEquals(10.0, TerritoryCalculator.radiusForArea(Math.PI * 100.0), 0.000001);
    }

    @Test
    void containmentIsHorizontalAndIgnoresVerticalPosition() {
        SanctuaryPosition center = new SanctuaryPosition("world", 0, 64, 0);
        double area = Math.PI * 100.0;

        assertTrue(TerritoryCalculator.contains(center, area, "world", 10.5, 0.5));
        assertTrue(TerritoryCalculator.contains(center, area, "world", 6.5, 8.5));
        assertFalse(TerritoryCalculator.contains(center, area, "world", 10.51, 0.5));
        assertFalse(TerritoryCalculator.contains(center, area, "world_nether", 0.5, 0.5));
    }

    @Test
    void spacingUsesTwoMaximumRadiiPlusMargin() {
        assertEquals(144.0, TerritoryCalculator.minimumAnchorDistance(64.0, 16.0));
    }
}
