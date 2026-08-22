package dev.liamtolkkinen.sanctuary.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.liamtolkkinen.sanctuary.sanctuary.Sanctuary;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryPosition;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryState;
import dev.liamtolkkinen.sanctuary.sanctuary.SanctuaryType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSanctuaryRepositoryTest {
    @TempDir
    Path tempDirectory;

    private SqliteSanctuaryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager databaseManager = new DatabaseManager(
            tempDirectory.resolve("sanctuary.db")
        );
        new MigrationRunner(databaseManager).migrate();
        repository = new SqliteSanctuaryRepository(databaseManager);
    }

    @Test
    void saveAndLoadPreservesSanctuaryState() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant created = Instant.parse("2026-08-21T12:00:00Z");
        Instant updated = Instant.parse("2026-08-21T12:01:00Z");
        Sanctuary expected = new Sanctuary(
            id,
            ownerId,
            SanctuaryType.BEACON,
            "Mountain Keep",
            Optional.of(new SanctuaryPosition("world", 100, 72, -45)),
            1,
            3,
            144.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            created,
            updated
        );

        repository.save(expected);

        Sanctuary actual = repository.findById(id).orElseThrow();
        assertEquals(expected, actual);
    }

    @Test
    void saveUpdatesExistingSanctuaryWithoutChangingCreationTime() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant created = Instant.parse("2026-08-21T12:00:00Z");
        Sanctuary active = new Sanctuary(
            id,
            ownerId,
            SanctuaryType.BEACON,
            "Home",
            Optional.of(new SanctuaryPosition("world", 0, 64, 0)),
            1,
            1,
            100.0,
            SanctuaryState.ACTIVE,
            Optional.empty(),
            Optional.empty(),
            created,
            created
        );
        repository.save(active);

        Sanctuary inactive = new Sanctuary(
            id,
            ownerId,
            SanctuaryType.BEACON,
            "Home",
            Optional.empty(),
            1,
            1,
            100.0,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            created,
            created.plusSeconds(30)
        );
        repository.save(inactive);

        assertEquals(inactive, repository.findById(id).orElseThrow());
    }

    @Test
    void destroyedStateAndAuditMetadataRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant created = Instant.parse("2026-08-21T12:00:00Z");
        Instant destroyedAt = created.plusSeconds(60);
        Sanctuary destroyed = new Sanctuary(
            id,
            ownerId,
            SanctuaryType.BEACON,
            "Lost Keep",
            Optional.empty(),
            1,
            2,
            100.0,
            SanctuaryState.DESTROYED,
            Optional.of(destroyedAt),
            Optional.of("DESPAWN"),
            created,
            destroyedAt
        );

        repository.save(destroyed);

        assertEquals(destroyed, repository.findById(id).orElseThrow());
    }

    @Test
    void findByOwnerAndFindAllReturnExpectedSanctuaries() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-21T12:00:00Z");

        repository.save(inactive(UUID.randomUUID(), owner, "Home", now));
        repository.save(inactive(UUID.randomUUID(), owner, "Village", now.plusSeconds(1)));
        repository.save(inactive(UUID.randomUUID(), otherOwner, "Other", now.plusSeconds(2)));

        var ownerResults = repository.findByOwner(owner);
        var allResults = repository.findAll();

        assertEquals(2, ownerResults.size());
        assertTrue(ownerResults.stream().allMatch(value -> value.ownerId().equals(owner)));
        assertEquals(3, allResults.size());
    }

    private static Sanctuary inactive(
        UUID id,
        UUID owner,
        String name,
        Instant created
    ) {
        return new Sanctuary(
            id,
            owner,
            SanctuaryType.BEACON,
            name,
            Optional.empty(),
            1,
            1,
            100.0,
            SanctuaryState.INACTIVE,
            Optional.empty(),
            Optional.empty(),
            created,
            created
        );
    }
}
