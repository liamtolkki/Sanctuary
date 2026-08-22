package dev.liamtolkkinen.sanctuary.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class MigrationRunner {
    private static final List<DatabaseMigration> MIGRATIONS = List.of(
        new DatabaseMigration(
            1,
            "create_sanctuaries",
            "/db/migration/V001__create_sanctuaries.sql"
        ),
        new DatabaseMigration(
            2,
            "anchor_lifecycle",
            "/db/migration/V002__anchor_lifecycle.sql"
        ),
        new DatabaseMigration(
            3,
            "territory_debug_beacons",
            "/db/migration/V003__territory_debug_beacons.sql"
        ),
        new DatabaseMigration(
            4,
            "territory_radius",
            "/db/migration/V004__territory_radius.sql"
        ),
        new DatabaseMigration(
            5,
            "trust_capabilities",
            "/db/migration/V005__trust_capabilities.sql"
        ),
        new DatabaseMigration(
            6,
            "security_policy",
            "/db/migration/V006__security_policy.sql"
        ),
        new DatabaseMigration(
            7,
            "beacon_effect_levels",
            "/db/migration/V007__beacon_effect_levels.sql"
        )
    );

    private final DatabaseManager databaseManager;

    public MigrationRunner(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void migrate() throws SQLException, IOException {
        try (Connection connection = databaseManager.openConnection()) {
            ensureMigrationTable(connection);

            for (DatabaseMigration migration : MIGRATIONS) {
                if (!isApplied(connection, migration.version())) {
                    apply(connection, migration);
                }
            }
        }
    }

    private static void ensureMigrationTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    applied_at TEXT NOT NULL
                )
                """);
        }
    }

    private static boolean isApplied(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT 1 FROM schema_migrations WHERE version = ?"
        )) {
            statement.setInt(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void apply(
        Connection connection,
        DatabaseMigration migration
    ) throws SQLException, IOException {
        String sql = readResource(migration.resourcePath());
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String statementSql : splitStatements(sql)) {
                try (var statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            if (migration.version() == 4) {
                backfillTerritoryRadius(connection);
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO schema_migrations(version, name, applied_at)
                VALUES (?, ?, ?)
                """)) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.name());
                statement.setString(3, Instant.now().toString());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static void backfillTerritoryRadius(Connection connection) throws SQLException {
        java.util.List<String> ids = new java.util.ArrayList<>();
        java.util.List<Double> radii = new java.util.ArrayList<>();
        try (
            var select = connection.createStatement();
            ResultSet rows = select.executeQuery("SELECT id, territory_area FROM sanctuaries")
        ) {
            while (rows.next()) {
                double area = rows.getDouble("territory_area");
                if (!Double.isFinite(area) || area <= 0.0) {
                    throw new SQLException("Cannot migrate invalid territory area for " + rows.getString("id"));
                }
                ids.add(rows.getString("id"));
                radii.add(Math.sqrt(area / Math.PI));
            }
        }

        try (var update = connection.prepareStatement(
            "UPDATE sanctuaries SET territory_radius = ? WHERE id = ?"
        )) {
            for (int index = 0; index < ids.size(); index++) {
                update.setDouble(1, radii.get(index));
                update.setString(2, ids.get(index));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = MigrationRunner.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing migration resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> splitStatements(String sql) {
        String withoutComments = sql.replaceAll("(?m)^\s*--.*$", "");
        return java.util.Arrays.stream(withoutComments.split(";"))
            .map(String::trim)
            .filter(statement -> !statement.isEmpty())
            .toList();
    }
}
