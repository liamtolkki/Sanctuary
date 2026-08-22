package dev.liamtolkkinen.sanctuary.trust;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SanctuaryTrustEntry(
    UUID sanctuaryId,
    UUID playerId,
    Instant createdAt,
    Set<SanctuaryCapability> capabilities
) {
    public SanctuaryTrustEntry {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(createdAt, "createdAt");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }
}
