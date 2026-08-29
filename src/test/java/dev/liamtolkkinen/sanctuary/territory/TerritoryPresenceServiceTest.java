package dev.liamtolkkinen.sanctuary.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TerritoryPresenceServiceTest {
    private final TerritoryPresenceService service = new TerritoryPresenceService();

    @Test
    void ignoresInactiveAndDestroyedSanctuaries() {
        Sanctuary inactive = sanctuary("Inactive", 0, 0, 100.0, SanctuaryState.INACTIVE);
        Sanctuary destroyed = destroyed("Destroyed");

        assertTrue(service.findCurrentSanctuary(
            List.of(inactive, destroyed), "world", 0.5, 64.5, 0.5
        ).isEmpty());
    }

    @Test
    void closestAnchorWinsWhenTerritoriesOverlap() {
        double radius = 10.0;
        Sanctuary first = sanctuary("First", 0, 0, radius, SanctuaryState.ACTIVE);
        Sanctuary second = sanctuary("Second", 8, 0, radius, SanctuaryState.ACTIVE);

        Sanctuary result = service.findCurrentSanctuary(
            List.of(first, second), "world", 7.5, 64.5, 0.5
        ).orElseThrow();

        assertEquals(second.id(), result.id());
    }

    @Test
    void returnsEmptyOutsideAllTerritories() {
        Sanctuary sanctuary = sanctuary("Test", 0, 0, 5.0, SanctuaryState.ACTIVE);
        assertTrue(service.findCurrentSanctuary(
            List.of(sanctuary), "world", 20.0, 64.5, 20.0
        ).isEmpty());
    }

    @Test
    void flyingAboveEllipsoidLeavesTerritory() {
        Sanctuary sanctuary = sanctuary("Test", 0, 0, 96.0, SanctuaryState.ACTIVE);

        assertTrue(service.findCurrentSanctuary(
            List.of(sanctuary), "world", 0.5, 64.5 + 63.9, 0.5
        ).isPresent());
        assertTrue(service.findCurrentSanctuary(
            List.of(sanctuary), "world", 0.5, 64.5 + 64.1, 0.5
        ).isEmpty());
    }

    private static Sanctuary sanctuary(
        String name,
        int x,
        int z,
        double radius,
        SanctuaryState state
    ) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            name,
            state == SanctuaryState.ACTIVE
                ? Optional.of(new SanctuaryPosition("world", x, 64, z))
                : Optional.empty(),
            1,
            1,
            radius,
            state,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }

    private static Sanctuary destroyed(String name) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            name,
            Optional.empty(),
            1,
            1,
            100.0,
            SanctuaryState.DESTROYED,
            Optional.of(now),
            Optional.of("TEST"),
            false,
            now,
            now
        );
    }
}
