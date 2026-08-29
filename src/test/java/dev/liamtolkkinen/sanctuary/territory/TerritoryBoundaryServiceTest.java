package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerritoryBoundaryServiceTest {
    @Test
    void pointCountUsesCircumferenceAndMinimumDensity() {
        assertEquals(12, TerritoryBoundaryService.pointCount(1.0, 10.0));
        assertEquals(76, TerritoryBoundaryService.pointCount(18.0, 1.5));
    }

    @Test
    void proximityHalfHeightShrinksAtMaximumDistance() {
        assertEquals(0.0, TerritoryBoundaryService.proximityHalfHeight(12.0, 12.0), 0.000001);
        assertEquals(Math.sqrt(80.0), TerritoryBoundaryService.proximityHalfHeight(8.0, 12.0), 0.000001);
        assertEquals(12.0, TerritoryBoundaryService.proximityHalfHeight(0.0, 12.0), 0.000001);
    }

    @Test
    void proximityBandUsesExclusiveMinimumAndMaximum() {
        assertFalse(TerritoryBoundaryService.isWithinProximityBand(3.0, 3.0, 12.0));
        assertTrue(TerritoryBoundaryService.isWithinProximityBand(3.01, 3.0, 12.0));
        assertTrue(TerritoryBoundaryService.isWithinProximityBand(11.99, 3.0, 12.0));
        assertFalse(TerritoryBoundaryService.isWithinProximityBand(12.0, 3.0, 12.0));
        assertFalse(TerritoryBoundaryService.isWithinProximityBand(2.0, 3.0, 12.0));
    }

    @Test
    void proximityBandAppliesToThreeDimensionalParticleDistance() {
        double horizontalDistance = 2.0;

        assertFalse(
            TerritoryBoundaryService.isWithinProximityBand(
                Math.hypot(horizontalDistance, 0.0),
                3.0,
                12.0
            )
        );
        assertTrue(
            TerritoryBoundaryService.isWithinProximityBand(
                Math.hypot(horizontalDistance, 3.0),
                3.0,
                12.0
            )
        );
        assertFalse(
            TerritoryBoundaryService.isWithinProximityBand(
                Math.hypot(horizontalDistance, 12.0),
                3.0,
                12.0
            )
        );
    }

    @Test
    void automaticBoundaryStaysHiddenDeepInsideTerritory() {
        assertFalse(
            TerritoryBoundaryService.automaticBoundaryVisible(
                true,
                24.0,
                0.0,
                16.0
            )
        );
    }

    @Test
    void automaticBoundaryShowsNearSurfaceFromInside() {
        assertTrue(
            TerritoryBoundaryService.automaticBoundaryVisible(
                true,
                8.0,
                0.0,
                16.0
            )
        );
    }

    @Test
    void automaticBoundaryShowsOutsideVolumeWhenHorizontallyOverFootprint() {
        assertTrue(
            TerritoryBoundaryService.automaticBoundaryVisible(
                false,
                80.0,
                0.0,
                16.0
            )
        );
    }

    @Test
    void automaticBoundaryShowsOutsideVolumeNearHorizontalFootprintEdge() {
        assertTrue(
            TerritoryBoundaryService.automaticBoundaryVisible(
                false,
                80.0,
                15.99,
                16.0
            )
        );
    }

    @Test
    void automaticBoundaryHidesOutsideVolumeBeyondHorizontalRange() {
        assertFalse(
            TerritoryBoundaryService.automaticBoundaryVisible(
                false,
                8.0,
                16.0,
                16.0
            )
        );
    }

    @Test
    void proximityBandRejectsInvalidBounds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TerritoryBoundaryService.isWithinProximityBand(5.0, 12.0, 12.0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TerritoryBoundaryService.isWithinProximityBand(5.0, -1.0, 12.0)
        );
    }
}
