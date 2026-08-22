package dev.liamtolkkinen.sanctuary.trust;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SanctuaryTrustRepository {
    boolean isTrusted(UUID sanctuaryId, UUID playerId) throws SQLException;

    List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) throws SQLException;

    Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId) throws SQLException;

    void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) throws SQLException;

    void removeTrusted(UUID sanctuaryId, UUID playerId) throws SQLException;

    void setCapability(
        UUID sanctuaryId,
        UUID playerId,
        SanctuaryCapability capability,
        boolean allowed
    ) throws SQLException;
}
