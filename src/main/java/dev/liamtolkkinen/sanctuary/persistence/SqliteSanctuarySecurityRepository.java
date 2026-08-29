package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.security.SanctuaryBlacklistEntry;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityMode;
import dev.liamtolkkinen.sanctuary.security.SanctuarySecurityRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteSanctuarySecurityRepository implements SanctuarySecurityRepository {
    private final DatabaseManager databaseManager;

    public SqliteSanctuarySecurityRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public SanctuarySecurityMode getMode(UUID sanctuaryId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT security_mode
                FROM sanctuary_security
                WHERE sanctuary_id = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            try (var result = statement.executeQuery()) {
                return result.next()
                    ? SanctuarySecurityMode.valueOf(result.getString("security_mode"))
                    : SanctuarySecurityMode.NORMAL;
            }
        }
    }

    @Override
    public void setMode(UUID sanctuaryId, SanctuarySecurityMode mode) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                INSERT INTO sanctuary_security(sanctuary_id, security_mode)
                VALUES (?, ?)
                ON CONFLICT(sanctuary_id) DO UPDATE SET security_mode = excluded.security_mode
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, mode.name());
            statement.executeUpdate();
        }
    }

    @Override
    public boolean isBlacklisted(UUID sanctuaryId, UUID playerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT 1
                FROM sanctuary_blacklist
                WHERE sanctuary_id = ? AND player_uuid = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public List<SanctuaryBlacklistEntry> findBlacklistedPlayers(UUID sanctuaryId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT sanctuary_id, player_uuid, created_at
                FROM sanctuary_blacklist
                WHERE sanctuary_id = ?
                ORDER BY created_at ASC, player_uuid ASC
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            try (var result = statement.executeQuery()) {
                List<SanctuaryBlacklistEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(new SanctuaryBlacklistEntry(
                        UUID.fromString(result.getString("sanctuary_id")),
                        UUID.fromString(result.getString("player_uuid")),
                        Instant.parse(result.getString("created_at"))
                    ));
                }
                return List.copyOf(entries);
            }
        }
    }

    @Override
    public void addBlacklisted(UUID sanctuaryId, UUID playerId, Instant createdAt) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                INSERT INTO sanctuary_blacklist(sanctuary_id, player_uuid, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT(sanctuary_id, player_uuid) DO NOTHING
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, createdAt.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void removeBlacklisted(UUID sanctuaryId, UUID playerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                DELETE FROM sanctuary_blacklist
                WHERE sanctuary_id = ? AND player_uuid = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Instant> getAggressionUntil(UUID sanctuaryId, UUID playerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT hostile_until
                FROM sanctuary_aggression
                WHERE sanctuary_id = ? AND player_uuid = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next()
                    ? Optional.of(Instant.parse(result.getString("hostile_until")))
                    : Optional.empty();
            }
        }
    }

    @Override
    public void setAggressionUntil(UUID sanctuaryId, UUID playerId, Instant hostileUntil)
        throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                INSERT INTO sanctuary_aggression(sanctuary_id, player_uuid, hostile_until)
                VALUES (?, ?, ?)
                ON CONFLICT(sanctuary_id, player_uuid)
                DO UPDATE SET hostile_until = excluded.hostile_until
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            statement.setString(3, hostileUntil.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void clearAggression(UUID sanctuaryId, UUID playerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                DELETE FROM sanctuary_aggression
                WHERE sanctuary_id = ? AND player_uuid = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }
}
