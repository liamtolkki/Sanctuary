package dev.liamtolkkinen.sanctuary.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationRunnerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void migrationsAreAppliedExactlyOnce() throws Exception {
        Path databasePath = tempDirectory.resolve("sanctuary.db");
        DatabaseManager databaseManager = new DatabaseManager(databasePath);
        MigrationRunner runner = new MigrationRunner(databaseManager);

        runner.migrate();
        runner.migrate();

        assertTrue(Files.exists(databasePath));
        try (
            var connection = databaseManager.openConnection();
            var statement = connection.createStatement();
            var result = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations")
        ) {
            assertTrue(result.next());
            assertEquals(11, result.getInt(1));
        }

        try (
            var connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table'
                  AND name IN (
                      'sanctuaries',
                      'sanctuary_trust',
                      'sanctuary_capabilities',
                      'sanctuary_security',
                      'sanctuary_blacklist',
                      'sanctuary_effect_levels',
                      'sanctuary_sentry_defaults',
                      'sentries',
                      'sentry_overrides',
                      'altar_offering_progress',
                      'sanctuary_anchors',
                      'sanctuary_anchor_edges',
                      'anchor_effect_levels',
                      'anchor_upgrades',
                      'sanctuary_upgrades'
                  )
                """);
            var result = statement.executeQuery()
        ) {
            assertTrue(result.next());
            assertEquals(15, result.getInt(1));
        }
    }
}
