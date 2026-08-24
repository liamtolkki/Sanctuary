package dev.liamtolkkinen.sanctuary.sentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SentryDeathTransitionTest {
    @Test
    void removesOnlyEntityReferenceForVanillaDeathSequence() {
        UUID id = UUID.randomUUID();
        UUID sanctuaryId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-24T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-24T12:05:00Z");

        SentryRecord original = new SentryRecord(
            id,
            sanctuaryId,
            "sentry_skeleton",
            "world",
            10,
            64,
            -20,
            Optional.of(entityId),
            SentryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            createdAt,
            updatedAt
        );

        SentryRecord transitioned = SentryDeathTransition.withoutEntity(original);

        assertTrue(transitioned.entityId().isEmpty());
        assertEquals(original.id(), transitioned.id());
        assertEquals(original.sanctuaryId(), transitioned.sanctuaryId());
        assertEquals(original.itemId(), transitioned.itemId());
        assertEquals(original.world(), transitioned.world());
        assertEquals(original.x(), transitioned.x());
        assertEquals(original.y(), transitioned.y());
        assertEquals(original.z(), transitioned.z());
        assertEquals(original.state(), transitioned.state());
        assertEquals(original.respawnAt(), transitioned.respawnAt());
        assertEquals(original.recallDeadline(), transitioned.recallDeadline());
        assertEquals(original.createdAt(), transitioned.createdAt());
        assertEquals(original.updatedAt(), transitioned.updatedAt());
    }
}
