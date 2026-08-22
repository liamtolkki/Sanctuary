package dev.liamtolkkinen.sanctuary.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.liamtolkkinen.sanctuary.effect.SanctuaryEffect;
import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSanctuaryEffectRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingEffectLevelDefaultsToOneAndCanBeUpdated() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("sanctuary.db"));
        new MigrationRunner(databaseManager).migrate();
        SqliteSanctuaryRepository sanctuaryRepository = new SqliteSanctuaryRepository(databaseManager);
        Sanctuary sanctuary = createSanctuary();
        sanctuaryRepository.save(sanctuary);
        SqliteSanctuaryEffectRepository repository = new SqliteSanctuaryEffectRepository(databaseManager);

        assertEquals(1, repository.getLevel(sanctuary.id(), SanctuaryEffect.SPEED));

        repository.setLevel(sanctuary.id(), SanctuaryEffect.SPEED, 3);
        assertEquals(3, repository.getLevel(sanctuary.id(), SanctuaryEffect.SPEED));
    }

    @Test
    void repositoryRejectsLevelsAboveEffectMaximum() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(tempDirectory.resolve("sanctuary.db"));
        new MigrationRunner(databaseManager).migrate();
        SqliteSanctuaryEffectRepository repository = new SqliteSanctuaryEffectRepository(databaseManager);

        assertThrows(
            IllegalArgumentException.class,
            () -> repository.setLevel(UUID.randomUUID(), SanctuaryEffect.REGENERATION, 3)
        );
    }

    private static Sanctuary createSanctuary() {
        Instant now = Instant.parse("2026-08-22T20:00:00Z");
        return new Sanctuary(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SanctuaryType.BEACON,
            "Effect Test",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            5,
            1,
            96.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            false,
            now,
            now
        );
    }
}
