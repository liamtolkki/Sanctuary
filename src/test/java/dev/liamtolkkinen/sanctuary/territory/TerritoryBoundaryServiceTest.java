package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerritoryBoundaryServiceTest {
    @Test
    void pointCountUsesConfiguredSpacingAroundCircumference() {
        int points = TerritoryBoundaryService.pointCount(18.0, 1.5);
        assertEquals((int) Math.ceil((2.0 * Math.PI * 18.0) / 1.5), points);
    }

    @Test
    void pointCountHasMinimumForSmallTerritories() {
        assertTrue(TerritoryBoundaryService.pointCount(0.5, 10.0) >= 12);
    }

    @Test
    void proximityHeightGrowsAsViewerGetsCloser() {
        assertEquals(0.0, TerritoryBoundaryService.proximityHalfHeight(12.0, 12.0), 0.000001);
        assertEquals(Math.sqrt(80.0), TerritoryBoundaryService.proximityHalfHeight(8.0, 12.0), 0.000001);
        assertEquals(12.0, TerritoryBoundaryService.proximityHalfHeight(0.0, 12.0), 0.000001);
    }
}
