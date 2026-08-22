package dev.liamtolkkinen.sanctuary.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SanctuaryCommandTest {
    @Test
    void recoverAutocompleteOnlyIncludesNormalInactiveBeacons() {
        assertTrue(SanctuaryCommand.isRecoverableAutocompleteCandidate(
            sanctuary(SanctuaryState.INACTIVE, false)
        ));
        assertFalse(SanctuaryCommand.isRecoverableAutocompleteCandidate(
            sanctuary(SanctuaryState.DESTROYED, false)
        ));
        assertFalse(SanctuaryCommand.isRecoverableAutocompleteCandidate(
            sanctuary(SanctuaryState.ACTIVE, false)
        ));
        assertFalse(SanctuaryCommand.isRecoverableAutocompleteCandidate(
            sanctuary(SanctuaryState.INACTIVE, true)
        ));
    }

    private static Sanctuary sanctuary(SanctuaryState state, boolean debugEphemeral) {
        Instant now = Instant.parse("2026-08-22T00:00:00Z");
        Optional<Instant> destroyedAt = state == SanctuaryState.DESTROYED
            ? Optional.of(now)
            : Optional.empty();
        Optional<String> reason = state == SanctuaryState.DESTROYED
            ? Optional.of("TEST")
            : Optional.empty();
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            "Test",
            state == SanctuaryState.ACTIVE
                ? Optional.of(new SanctuaryPosition("world", 0, 64, 0))
                : Optional.empty(),
            1,
            1,
            100.0,
            state,
            destroyedAt,
            reason,
            debugEphemeral,
            now,
            now
        );
    }
}
