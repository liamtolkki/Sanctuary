package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.trust.SanctuaryCapability;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustEntry;
import dev.liamtolkkinen.sanctuary.trust.SanctuaryTrustRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SqliteSanctuaryTrustRepository implements SanctuaryTrustRepository {
    private final DatabaseManager databaseManager;

    public SqliteSanctuaryTrustRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean isTrusted(UUID sanctuaryId, UUID playerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT 1
                FROM sanctuary_trust
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
    public List<SanctuaryTrustEntry> findTrustedPlayers(UUID sanctuaryId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT sanctuary_id, player_uuid, created_at
                FROM sanctuary_trust
                WHERE sanctuary_id = ?
                ORDER BY created_at ASC, player_uuid ASC
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            try (var result = statement.executeQuery()) {
                List<SanctuaryTrustEntry> entries = new ArrayList<>();
                while (result.next()) {
                    UUID playerId = UUID.fromString(result.getString("player_uuid"));
                    entries.add(new SanctuaryTrustEntry(
                        UUID.fromString(result.getString("sanctuary_id")),
                        playerId,
                        Instant.parse(result.getString("created_at")),
                        findCapabilities(sanctuaryId, playerId)
                    ));
                }
                return List.copyOf(entries);
            }
        }
    }

    @Override
    public Set<SanctuaryCapability> findCapabilities(UUID sanctuaryId, UUID playerId)
        throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT capability
                FROM sanctuary_capabilities
                WHERE sanctuary_id = ? AND player_uuid = ?
                ORDER BY capability ASC
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            try (var result = statement.executeQuery()) {
                EnumSet<SanctuaryCapability> capabilities = EnumSet.noneOf(SanctuaryCapability.class);
                while (result.next()) {
                    capabilities.add(SanctuaryCapability.valueOf(result.getString("capability")));
                }
                return Set.copyOf(capabilities);
            }
        }
    }

    @Override
    public void addTrusted(UUID sanctuaryId, UUID playerId, Instant createdAt) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                INSERT INTO sanctuary_trust(sanctuary_id, player_uuid, created_at)
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
    public void removeTrusted(UUID sanctuaryId, UUID playerId) throws SQLException {
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                DELETE FROM sanctuary_trust
                WHERE sanctuary_id = ? AND player_uuid = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void setCapability(
        UUID sanctuaryId,
        UUID playerId,
        SanctuaryCapability capability,
        boolean allowed
    ) throws SQLException {
        try (Connection connection = databaseManager.openConnection()) {
            if (allowed) {
                try (var statement = connection.prepareStatement("""
                    INSERT INTO sanctuary_capabilities(sanctuary_id, player_uuid, capability)
                    VALUES (?, ?, ?)
                    ON CONFLICT(sanctuary_id, player_uuid, capability) DO NOTHING
                    """)) {
                    statement.setString(1, sanctuaryId.toString());
                    statement.setString(2, playerId.toString());
                    statement.setString(3, capability.name());
                    statement.executeUpdate();
                }
            } else {
                try (var statement = connection.prepareStatement("""
                    DELETE FROM sanctuary_capabilities
                    WHERE sanctuary_id = ? AND player_uuid = ? AND capability = ?
                    """)) {
                    statement.setString(1, sanctuaryId.toString());
                    statement.setString(2, playerId.toString());
                    statement.setString(3, capability.name());
                    statement.executeUpdate();
                }
            }
        }
    }
}
