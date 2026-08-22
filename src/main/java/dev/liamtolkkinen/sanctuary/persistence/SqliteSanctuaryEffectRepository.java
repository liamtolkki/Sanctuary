package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffect;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffectRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class SqliteSanctuaryEffectRepository implements SanctuaryEffectRepository {
    private final DatabaseManager databaseManager;

    public SqliteSanctuaryEffectRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public int getLevel(UUID sanctuaryId, SanctuaryEffect effect) throws SQLException {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(effect, "effect");
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT level
                FROM sanctuary_effect_levels
                WHERE sanctuary_id = ? AND effect = ?
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, effect.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("level") : 1;
            }
        }
    }

    @Override
    public void setLevel(UUID sanctuaryId, SanctuaryEffect effect, int level) throws SQLException {
        Objects.requireNonNull(sanctuaryId, "sanctuaryId");
        Objects.requireNonNull(effect, "effect");
        if (level < 1 || level > effect.maximumLevel()) {
            throw new IllegalArgumentException(
                effect.name() + " level must be between 1 and " + effect.maximumLevel()
            );
        }
        try (
            Connection connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                INSERT INTO sanctuary_effect_levels (sanctuary_id, effect, level)
                VALUES (?, ?, ?)
                ON CONFLICT(sanctuary_id, effect) DO UPDATE SET level = excluded.level
                """)
        ) {
            statement.setString(1, sanctuaryId.toString());
            statement.setString(2, effect.name());
            statement.setInt(3, level);
            statement.executeUpdate();
        }
    }
}
