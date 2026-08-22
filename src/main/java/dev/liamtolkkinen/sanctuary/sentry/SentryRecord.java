package dev.liamtolkkinen.sanctuary.sentry;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record SentryRecord(
    UUID id,
    UUID sanctuaryId,
    String itemId,
    String world,
    int x,
    int y,
    int z,
    Optional<UUID> entityId,
    SentryState state,
    Optional<Instant> respawnAt,
    Optional<Instant> recallDeadline,
    Instant createdAt,
    Instant updatedAt
) {}
