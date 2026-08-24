package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.liamtolkkinen.sanctuary.anchor.SanctuaryAnchor;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerritoryAreaCalculatorTest {
    private static final double EPSILON = 0.000001;
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void singleActiveAnchorUsesCurrentRadius() {
        SanctuaryAnchor anchor = anchor("world", 0, 0, 18.0, SanctuaryState.ACTIVE);

        assertEquals(Math.PI * 18.0 * 18.0, TerritoryAreaCalculator.currentUnionArea(List.of(anchor)), EPSILON);
    }

    @Test
    void inactiveAnchorsAreExcluded() {
        SanctuaryAnchor active = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);
        SanctuaryAnchor inactive = anchor("world", 100, 0, 96.0, SanctuaryState.INACTIVE);

        assertEquals(Math.PI * 100.0, TerritoryAreaCalculator.currentUnionArea(List.of(active, inactive)), EPSILON);
    }

    @Test
    void disjointCirclesAddTheirAreas() {
        SanctuaryAnchor first = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);
        SanctuaryAnchor second = anchor("world", 30, 0, 5.0, SanctuaryState.ACTIVE);

        assertEquals(Math.PI * 125.0, TerritoryAreaCalculator.currentUnionArea(List.of(first, second)), EPSILON);
    }

    @Test
    void containedCircleDoesNotIncreaseUnionArea() {
        SanctuaryAnchor outer = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);
        SanctuaryAnchor inner = anchor("world", 2, 0, 3.0, SanctuaryState.ACTIVE);

        assertEquals(Math.PI * 100.0, TerritoryAreaCalculator.currentUnionArea(List.of(outer, inner)), EPSILON);
    }

    @Test
    void duplicateCirclesCountOnce() {
        SanctuaryAnchor first = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);
        SanctuaryAnchor duplicate = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);

        assertEquals(Math.PI * 100.0, TerritoryAreaCalculator.currentUnionArea(List.of(first, duplicate)), EPSILON);
    }

    @Test
    void overlappingEqualCirclesSubtractLensOverlap() {
        SanctuaryAnchor first = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);
        SanctuaryAnchor second = anchor("world", 10, 0, 10.0, SanctuaryState.ACTIVE);
        double overlap = 2.0 * 100.0 * Math.acos(0.5) - 5.0 * Math.sqrt(300.0);
        double expected = 2.0 * Math.PI * 100.0 - overlap;

        assertEquals(expected, TerritoryAreaCalculator.currentUnionArea(List.of(first, second)), EPSILON);
    }

    @Test
    void sameCoordinatesInDifferentWorldsDoNotOverlap() {
        SanctuaryAnchor overworld = anchor("world", 0, 0, 10.0, SanctuaryState.ACTIVE);
        SanctuaryAnchor nether = anchor("world_nether", 0, 0, 10.0, SanctuaryState.ACTIVE);

        assertEquals(2.0 * Math.PI * 100.0, TerritoryAreaCalculator.currentUnionArea(List.of(overworld, nether)), EPSILON);
    }

    private static SanctuaryAnchor anchor(
        String world,
        int x,
        int z,
        double radius,
        SanctuaryState state
    ) {
        Optional<SanctuaryPosition> position = state == SanctuaryState.DESTROYED
            ? Optional.empty()
            : Optional.of(new SanctuaryPosition(world, x, 64, z));
        return new SanctuaryAnchor(
            UUID.randomUUID(),
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            Optional.empty(),
            SanctuaryType.BEACON,
            position,
            1,
            1,
            radius,
            state,
            Optional.empty(),
            Optional.empty(),
            NOW,
            NOW
        );
    }
}
