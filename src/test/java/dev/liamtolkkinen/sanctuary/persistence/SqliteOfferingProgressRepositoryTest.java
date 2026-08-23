package dev.liamtolkkinen.sanctuary.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteOfferingProgressRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void progressAdvancesExactlyOnceAndPersistsFinalRewardState() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("sanctuary.db"));
        new MigrationRunner(databaseManager).migrate();
        SqliteOfferingProgressRepository repository = new SqliteOfferingProgressRepository(databaseManager);
        UUID playerId = UUID.randomUUID();

        assertEquals(0, repository.completedOfferings(playerId));
        assertFalse(repository.divineRelicAwarded(playerId));

        assertTrue(repository.advance(playerId, 0));
        assertFalse(repository.advance(playerId, 0));
        assertEquals(1, repository.completedOfferings(playerId));

        for (int completed = 1; completed < 12; completed++) {
            assertTrue(repository.advance(playerId, completed));
        }
        assertEquals(12, repository.completedOfferings(playerId));
        assertFalse(repository.divineRelicAwarded(playerId));

        repository.markDivineRelicAwarded(playerId);
        assertTrue(repository.divineRelicAwarded(playerId));
    }
}
