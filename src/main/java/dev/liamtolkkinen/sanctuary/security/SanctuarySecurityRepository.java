package dev.liamtolkkinen.sanctuary.security;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SanctuarySecurityRepository {
    SanctuarySecurityMode getMode(UUID sanctuaryId) throws SQLException;

    void setMode(UUID sanctuaryId, SanctuarySecurityMode mode) throws SQLException;

    boolean isBlacklisted(UUID sanctuaryId, UUID playerId) throws SQLException;

    List<SanctuaryBlacklistEntry> findBlacklistedPlayers(UUID sanctuaryId) throws SQLException;

    void addBlacklisted(UUID sanctuaryId, UUID playerId, Instant createdAt) throws SQLException;

    void removeBlacklisted(UUID sanctuaryId, UUID playerId) throws SQLException;

    default Optional<Instant> getAggressionUntil(UUID sanctuaryId, UUID playerId) throws SQLException {
        return Optional.empty();
    }

    default void setAggressionUntil(UUID sanctuaryId, UUID playerId, Instant hostileUntil)
        throws SQLException {
    }

    default void clearAggression(UUID sanctuaryId, UUID playerId) throws SQLException {
    }
}
