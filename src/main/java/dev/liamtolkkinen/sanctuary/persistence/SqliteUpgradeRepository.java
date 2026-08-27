package dev.liamtolkkinen.sanctuary.persistence;

import dev.liamtolkkinen.sanctuary.upgrade.AnchorUpgradeType;
import dev.liamtolkkinen.sanctuary.upgrade.SanctuaryUpgradeType;
import dev.liamtolkkinen.sanctuary.upgrade.UpgradeRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SqliteUpgradeRepository implements UpgradeRepository {
    private final DatabaseManager databaseManager;

    public SqliteUpgradeRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    @Override
    public boolean hasAnchorUpgrade(UUID anchorId, AnchorUpgradeType upgrade) throws SQLException {
        return exists("anchor_upgrades", "anchor_id", anchorId, upgrade.name());
    }

    @Override
    public void installAnchorUpgrade(UUID anchorId, AnchorUpgradeType upgrade, Instant installedAt)
        throws SQLException {
        install("anchor_upgrades", "anchor_id", anchorId, upgrade.name(), installedAt);
    }

    @Override
    public boolean hasSanctuaryUpgrade(UUID sanctuaryId, SanctuaryUpgradeType upgrade)
        throws SQLException {
        return exists("sanctuary_upgrades", "sanctuary_id", sanctuaryId, upgrade.name());
    }

    @Override
    public void installSanctuaryUpgrade(
        UUID sanctuaryId,
        SanctuaryUpgradeType upgrade,
        Instant installedAt
    ) throws SQLException {
        install("sanctuary_upgrades", "sanctuary_id", sanctuaryId, upgrade.name(), installedAt);
    }

    private boolean exists(String table, String idColumn, UUID id, String upgrade) throws SQLException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(upgrade, "upgrade");
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement(
                 "SELECT 1 FROM " + table + " WHERE " + idColumn + " = ? AND upgrade_type = ?"
             )) {
            statement.setString(1, id.toString());
            statement.setString(2, upgrade);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void install(
        String table,
        String idColumn,
        UUID id,
        String upgrade,
        Instant installedAt
    ) throws SQLException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(upgrade, "upgrade");
        Objects.requireNonNull(installedAt, "installedAt");
        try (Connection connection = databaseManager.openConnection();
             var statement = connection.prepareStatement(
                 "INSERT OR IGNORE INTO " + table
                     + "(" + idColumn + ", upgrade_type, installed_at) VALUES (?, ?, ?)"
             )) {
            statement.setString(1, id.toString());
            statement.setString(2, upgrade);
            statement.setString(3, installedAt.toString());
            statement.executeUpdate();
        }
    }
}
