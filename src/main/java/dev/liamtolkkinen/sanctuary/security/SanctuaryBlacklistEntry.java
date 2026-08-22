package dev.liamtolkkinen.sanctuary.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SanctuaryBlacklistEntry(UUID sanctuaryId, UUID playerId, Instant createdAt) {
    public SanctuaryBlacklistEntry {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
