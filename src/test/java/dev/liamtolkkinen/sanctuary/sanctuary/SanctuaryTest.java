package dev.liamtolkkinen.sanctuary.sanctuary;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SanctuaryTest {
    @Test
    void activeSanctuaryRequiresPosition() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");

        assertThrows(
            IllegalArgumentException.class,
            () -> new Sanctuary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SanctuaryType.BEACON,
                "Home",
                Optional.empty(),
                1,
                100.0,
                SanctuaryState.ACTIVE,
                now,
                now
            )
        );
    }

    @Test
    void inactiveSanctuaryMayHaveNoPosition() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");

        new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            "Home",
            Optional.empty(),
            1,
            100.0,
            SanctuaryState.INACTIVE,
            now,
            now
        );
    }

    @Test
    void invalidTierAndAreaAreRejected() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        SanctuaryPosition position = new SanctuaryPosition("world", 1, 64, 2);

        assertThrows(
            IllegalArgumentException.class,
            () -> new Sanctuary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SanctuaryType.BEACON,
                "Home",
                Optional.of(position),
                0,
                100.0,
                SanctuaryState.ACTIVE,
                now,
                now
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new Sanctuary(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SanctuaryType.BEACON,
                "Home",
                Optional.of(position),
                1,
                0.0,
                SanctuaryState.ACTIVE,
                now,
                now
            )
        );
    }
}
