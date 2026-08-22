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
            var result = statement.executeQuery(
                "SELECT COUNT(*) FROM schema_migrations"
            )
        ) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }

        try (
            var connection = databaseManager.openConnection();
            var statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table' AND name = 'sanctuaries'
                """);
            var result = statement.executeQuery()
        ) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }
}
