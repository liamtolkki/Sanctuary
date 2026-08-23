package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.altar.OfferingProgressRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class SqliteOfferingProgressRepository implements OfferingProgressRepository {
    private final DatabaseManager databaseManager;

    public SqliteOfferingProgressRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public int completedOfferings(UUID playerId) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT completed_offerings
                FROM altar_offering_progress
                WHERE player_uuid = ?
                """)
        ) {
            statement.setString(1, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getInt("completed_offerings") : 0;
            }
        }
    }

    @Override
    public boolean advance(UUID playerId, int expectedCompleted) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        if (expectedCompleted < 0 || expectedCompleted >= 12) {
            throw new IllegalArgumentException("expectedCompleted must be between 0 and 11");
        }

        try (Connection connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureRow(connection, playerId);
                try (var statement = connection.prepareStatement("""
                    UPDATE altar_offering_progress
                    SET completed_offerings = completed_offerings + 1
                    WHERE player_uuid = ? AND completed_offerings = ?
                    """)) {
                    statement.setString(1, playerId.toString());
                    statement.setInt(2, expectedCompleted);
                    boolean changed = statement.executeUpdate() == 1;
                    if (changed) {
                        connection.commit();
                    } else {
                        connection.rollback();
                    }
                    return changed;
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean divineRelicAwarded(UUID playerId) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT divine_relic_awarded
                FROM altar_offering_progress
                WHERE player_uuid = ?
                """)
        ) {
            statement.setString(1, playerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() && result.getInt("divine_relic_awarded") != 0;
            }
        }
    }

    @Override
    public void markDivineRelicAwarded(UUID playerId) throws SQLException {
        Objects.requireNonNull(playerId, "playerId");
        try (Connection connection = databaseManager.openConnection()) {
            ensureRow(connection, playerId);
            try (var statement = connection.prepareStatement("""
                UPDATE altar_offering_progress
                SET divine_relic_awarded = 1
                WHERE player_uuid = ? AND completed_offerings = 12
                """)) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
        }
    }

    private static void ensureRow(Connection connection, UUID playerId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO altar_offering_progress (player_uuid, completed_offerings, divine_relic_awarded)
            VALUES (?, 0, 0)
            ON CONFLICT(player_uuid) DO NOTHING
            """)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }
}
