package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.effect.AnchorEffectRepository;
import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffect;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class SqliteAnchorEffectRepository implements AnchorEffectRepository {
    private final DatabaseManager databaseManager;

    public SqliteAnchorEffectRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public int getLevel(UUID anchorId, SanctuaryEffect effect) throws SQLException {
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                 SELECT level FROM anchor_effect_levels
                 WHERE anchor_id = ? AND effect = ?
                 """)) {
            statement.setString(1, anchorId.toString());
            statement.setString(2, effect.name());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("level") : 1;
            }
        }
    }

    @Override
    public void setLevel(UUID anchorId, SanctuaryEffect effect, int level) throws SQLException {
        Objects.requireNonNull(anchorId, "anchorId");
        Objects.requireNonNull(effect, "effect");
        if (level < 1 || level > effect.maximumLevel()) {
            throw new IllegalArgumentException(
                effect.name() + " level must be between 1 and " + effect.maximumLevel()
            );
        }
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO anchor_effect_levels(anchor_id, effect, level)
                 VALUES (?, ?, ?)
                 ON CONFLICT(anchor_id, effect) DO UPDATE SET level = excluded.level
                 """)) {
            statement.setString(1, anchorId.toString());
            statement.setString(2, effect.name());
            statement.setInt(3, level);
            statement.executeUpdate();
        }
    }
}
